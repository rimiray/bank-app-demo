package com.bankapp.aicollateralservice.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads KEY=VALUE pairs from a .env file into Spring Environment.
 * Spring Boot does not read .env files automatically.
 */
public class EnvFileEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(EnvFileEnvironmentPostProcessor.class);
    private static final String PROPERTY_SOURCE_NAME = "dotenv";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path envFile = findEnvFile();
        if (envFile == null) {
            log.warn("No .env file found while starting ai-collateral-service");
            return;
        }

        Map<String, Object> properties = loadEnvFile(envFile);
        if (!properties.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
            log.info("Loaded {} entries from {}", properties.size(), envFile);
        }
    }

    private Path findEnvFile() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path dir = cwd; dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve(".env");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            if (dir.getParent() == null || dir.getParent().equals(dir)) {
                break;
            }
        }
        return null;
    }

    private Map<String, Object> loadEnvFile(Path envFile) {
        Map<String, Object> properties = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
                EnvFileParser.putLine(line, properties);
            }
        } catch (IOException ex) {
            log.warn("Failed to read .env file {}: {}", envFile, ex.getMessage());
        }
        return properties;
    }
}
