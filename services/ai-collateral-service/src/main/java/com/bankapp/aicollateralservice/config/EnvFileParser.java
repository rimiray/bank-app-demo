package com.bankapp.aicollateralservice.config;

import java.util.Map;

public final class EnvFileParser {

    private EnvFileParser() {
    }

    public static String sanitizeValue(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.trim();
        if (sanitized.startsWith("\uFEFF")) {
            sanitized = sanitized.substring(1).trim();
        }
        return stripQuotes(sanitized);
    }

    public static void putLine(String line, Map<String, Object> target) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return;
        }
        int separator = trimmed.indexOf('=');
        if (separator <= 0) {
            return;
        }
        String key = sanitizeValue(trimmed.substring(0, separator));
        if (key.startsWith("\uFEFF")) {
            key = key.substring(1).trim();
        }
        String value = sanitizeValue(trimmed.substring(separator + 1));
        if (!key.isEmpty()) {
            target.put(key, value);
        }
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            if ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'"))) {
                return value.substring(1, value.length() - 1).trim();
            }
        }
        return value;
    }
}
