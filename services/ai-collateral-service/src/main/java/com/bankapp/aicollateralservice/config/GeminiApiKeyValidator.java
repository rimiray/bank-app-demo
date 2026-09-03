package com.bankapp.aicollateralservice.config;

import jakarta.annotation.PostConstruct;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GeminiApiKeyValidator {

    private static final Logger log = LoggerFactory.getLogger(GeminiApiKeyValidator.class);
    private static final Set<String> PLACEHOLDER_KEYS = Set.of(
            "YOUR_GEMINI_API_KEY_HERE",
            "your_default_key_here"
    );

    private final String apiKey;

    public GeminiApiKeyValidator(@Value("${gemini.api.key:}") String apiKey) {
        this.apiKey = EnvFileParser.sanitizeValue(apiKey);
    }

    @PostConstruct
    void logKeyStatus() {
        if (!isConfigured()) {
            log.warn("GEMINI_API_KEY is not configured; collateral evaluation will use heuristic fallback");
            return;
        }
        log.info("GEMINI_API_KEY loaded (length={}, prefix={}***)",
                apiKey.length(),
                apiKey.substring(0, Math.min(4, apiKey.length())));
    }

    public boolean isConfigured() {
        return !apiKey.isBlank() && !PLACEHOLDER_KEYS.contains(apiKey);
    }

    public String getApiKey() {
        return apiKey;
    }
}
