package com.bankapp.aicollateralservice.service;

import com.bankapp.aicollateralservice.config.GeminiApiKeyValidator;
import com.bankapp.aicollateralservice.dto.CollateralEvaluationResponse;
import com.bankapp.aicollateralservice.exception.BadRequestException;
import com.bankapp.aicollateralservice.exception.GeminiApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class CollateralService {

    private static final Logger log = LoggerFactory.getLogger(CollateralService.class);

    private static final String SYSTEM_PROMPT = """
            Analyze this collateral item image. Identify object, physical condition, \
            and estimate fair market value in EUR. Respond strictly in valid raw JSON \
            with keys: objectDetected (string), condition (string), \
            estimatedValueEur (number), maxCreditLimitEur (number).
            """;

    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{[\\s\\S]*}");
    private static final BigDecimal CREDIT_LIMIT_RATIO = new BigDecimal("0.70");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final GeminiApiKeyValidator apiKeyValidator;
    private final String geminiBaseUrl;
    private final String geminiModel;
    private final int maxAttempts;
    private final long initialRetryDelayMs;
    private final BigDecimal fallbackEstimatedValueEur;

    public CollateralService(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            GeminiApiKeyValidator apiKeyValidator,
            @Value("${gemini.api.base-url}") String geminiBaseUrl,
            @Value("${gemini.api.model}") String geminiModel,
            @Value("${gemini.retry.max-attempts}") int maxAttempts,
            @Value("${gemini.retry.initial-delay-ms}") long initialRetryDelayMs,
            @Value("${gemini.fallback.estimated-value-eur}") BigDecimal fallbackEstimatedValueEur
    ) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiKeyValidator = apiKeyValidator;
        this.geminiBaseUrl = geminiBaseUrl;
        this.geminiModel = geminiModel;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialRetryDelayMs = Math.max(0, initialRetryDelayMs);
        this.fallbackEstimatedValueEur = fallbackEstimatedValueEur;
    }

    public CollateralEvaluationResponse evaluate(MultipartFile file) {
        validateFile(file);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception ex) {
            throw new BadRequestException("Failed to read uploaded file", ex);
        }

        if (bytes.length == 0) {
            throw new BadRequestException("Uploaded file is empty");
        }

        if (!apiKeyValidator.isConfigured()) {
            log.warn("Gemini API key is not configured; returning heuristic collateral estimate");
            return fallbackEvaluation(file);
        }

        String base64Image = Base64.getEncoder().encodeToString(bytes);
        String mimeType = resolveMimeType(file);

        try {
            String rawText = callGemini(base64Image, mimeType);
            return parseEvaluation(rawText);
        } catch (GeminiApiException ex) {
            log.warn("Gemini API unavailable (status={}, model={}): {}. Returning heuristic collateral estimate",
                    ex.getStatus().value(), geminiModel, ex.getMessage());
            return fallbackEvaluation(file);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required and must not be empty");
        }
    }

    private String resolveMimeType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank() || !contentType.startsWith("image/")) {
            return MediaType.IMAGE_JPEG_VALUE;
        }
        return contentType;
    }

    private String geminiEndpoint() {
        String base = geminiBaseUrl.endsWith("/")
                ? geminiBaseUrl.substring(0, geminiBaseUrl.length() - 1)
                : geminiBaseUrl;
        return UriComponentsBuilder
                .fromUriString(base + "/" + geminiModel + ":generateContent")
                .queryParam("key", apiKeyValidator.getApiKey())
                .toUriString();
    }

    /**
     * Retries transient Gemini failures (5xx, throttling, transport) with exponential backoff.
     */
    private String callGemini(String base64Image, String mimeType) {
        HttpEntity<Map<String, Object>> entity = buildGeminiRequest(base64Image, mimeType);
        String endpoint = geminiEndpoint();

        for (int attempt = 1; ; attempt++) {
            try {
                return executeGeminiCall(endpoint, entity);
            } catch (GeminiApiException ex) {
                if (!ex.isRetryable() || attempt >= maxAttempts) {
                    throw ex;
                }
                long delayMs = initialRetryDelayMs * (1L << (attempt - 1));
                log.warn("Gemini API attempt {}/{} failed (status={}): {}. Retrying in {} ms",
                        attempt, maxAttempts, ex.getStatus().value(), ex.getMessage(), delayMs);
                sleep(delayMs);
            }
        }
    }

    private HttpEntity<Map<String, Object>> buildGeminiRequest(String base64Image, String mimeType) {
        Map<String, Object> textPart = Map.of("text", SYSTEM_PROMPT);
        Map<String, Object> imagePart = Map.of("inline_data", Map.of(
                "mime_type", mimeType,
                "data", base64Image
        ));
        Map<String, Object> content = Map.of("parts", List.of(textPart, imagePart));
        Map<String, Object> requestBody = Map.of("contents", List.of(content));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(requestBody, headers);
    }

    @SuppressWarnings("rawtypes")
    private String executeGeminiCall(String endpoint, HttpEntity<Map<String, Object>> entity) {
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(endpoint, entity, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw geminiFailure("Empty response from Gemini API");
            }
            return extractTextFromGeminiResponse(response.getBody());
        } catch (HttpStatusCodeException ex) {
            throw GeminiApiException.from(ex, objectMapper);
        } catch (ResourceAccessException ex) {
            throw new GeminiApiException(HttpStatus.SERVICE_UNAVAILABLE, "Gemini API is unreachable", true);
        } catch (RestClientException ex) {
            throw geminiFailure("Gemini API request failed");
        }
    }

    private void sleep(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new GeminiApiException(HttpStatus.SERVICE_UNAVAILABLE, "Gemini API call was interrupted");
        }
    }

    private String extractTextFromGeminiResponse(Map<?, ?> body) {
        if (!(body.get("candidates") instanceof List<?> candidates) || candidates.isEmpty()) {
            throw geminiFailure("Gemini API response has no candidates");
        }
        if (!(candidates.getFirst() instanceof Map<?, ?> candidate)
                || !(candidate.get("content") instanceof Map<?, ?> content)
                || !(content.get("parts") instanceof List<?> parts)
                || parts.isEmpty()) {
            throw geminiFailure("Gemini API response has no content parts");
        }
        if (!(parts.getFirst() instanceof Map<?, ?> part)
                || !(part.get("text") instanceof String text)
                || text.isBlank()) {
            throw geminiFailure("Gemini API response has no text");
        }
        return text;
    }

    private CollateralEvaluationResponse parseEvaluation(String rawText) {
        String json = extractJson(rawText);
        JsonNode node;
        try {
            node = objectMapper.readTree(json);
        } catch (Exception ex) {
            throw geminiFailure("Gemini API returned malformed JSON");
        }

        String objectDetected = textOrNull(node, "objectDetected");
        String condition = textOrNull(node, "condition");
        BigDecimal estimatedValueEur = decimalOrNull(node, "estimatedValueEur");

        if (objectDetected == null || condition == null || estimatedValueEur == null) {
            throw geminiFailure("Gemini API response is missing required fields");
        }

        BigDecimal maxCreditLimitEur = decimalOrNull(node, "maxCreditLimitEur");
        if (maxCreditLimitEur == null) {
            maxCreditLimitEur = creditLimitOf(estimatedValueEur);
        }

        return CollateralEvaluationResponse.builder()
                .objectDetected(objectDetected)
                .condition(condition)
                .estimatedValueEur(estimatedValueEur)
                .maxCreditLimitEur(maxCreditLimitEur)
                .build();
    }

    /**
     * Heuristic estimate used when Gemini is unreachable, so the credit flow stays available.
     */
    private CollateralEvaluationResponse fallbackEvaluation(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        return CollateralEvaluationResponse.builder()
                .objectDetected(fileName != null && !fileName.isBlank()
                        ? "Unidentified collateral item (" + fileName + ")"
                        : "Unidentified collateral item")
                .condition("AI appraisal unavailable, manual review required")
                .estimatedValueEur(fallbackEstimatedValueEur)
                .maxCreditLimitEur(creditLimitOf(fallbackEstimatedValueEur))
                .build();
    }

    private BigDecimal creditLimitOf(BigDecimal estimatedValueEur) {
        return estimatedValueEur.multiply(CREDIT_LIMIT_RATIO).setScale(2, RoundingMode.HALF_UP);
    }

    private GeminiApiException geminiFailure(String message) {
        return new GeminiApiException(HttpStatus.BAD_GATEWAY, message);
    }

    private String extractJson(String rawText) {
        String trimmed = rawText.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        Matcher matcher = JSON_OBJECT_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group();
        }
        throw geminiFailure("Gemini API response contains no JSON object");
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isTextual()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }

    private BigDecimal decimalOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isNumber()) {
            return null;
        }
        return value.decimalValue();
    }
}
