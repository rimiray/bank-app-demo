package com.bankapp.aicollateralservice.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpStatusCodeException;

public class GeminiApiException extends RuntimeException {

    private final HttpStatus status;
    private final boolean retryable;

    public GeminiApiException(HttpStatus status, String message) {
        this(status, message, false);
    }

    public GeminiApiException(HttpStatus status, String message, boolean retryable) {
        super(message);
        this.status = status;
        this.retryable = retryable;
    }

    public HttpStatus getStatus() {
        return status;
    }

    /**
     * True for transient failures (5xx, throttling, transport errors) worth another attempt.
     */
    public boolean isRetryable() {
        return retryable;
    }

    public static GeminiApiException from(HttpStatusCodeException ex, ObjectMapper objectMapper) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.BAD_GATEWAY;
        }

        String reason = extractReason(ex.getResponseBodyAsString(), objectMapper);
        if ("API_KEY_INVALID".equals(reason) || status == HttpStatus.UNAUTHORIZED) {
            return new GeminiApiException(status, "Invalid Gemini API Key. Check GEMINI_API_KEY in .env");
        }
        if (status == HttpStatus.NOT_FOUND) {
            return new GeminiApiException(status, "Gemini model not found. Check GEMINI_MODEL in .env");
        }
        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            return new GeminiApiException(status, "Gemini API rate limit exceeded", true);
        }
        if (status == HttpStatus.BAD_REQUEST) {
            return new GeminiApiException(status, "Invalid Gemini API request payload");
        }
        if (status.is5xxServerError()) {
            return new GeminiApiException(status, "Gemini API is temporarily unavailable", true);
        }

        return new GeminiApiException(status, "Gemini API request failed");
    }

    private static String extractReason(String responseBody, ObjectMapper objectMapper) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode details = root.path("error").path("details");
            if (details.isArray()) {
                for (JsonNode detail : details) {
                    String reason = detail.path("reason").asText(null);
                    if (reason != null && !reason.isBlank()) {
                        return reason;
                    }
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return null;
    }
}
