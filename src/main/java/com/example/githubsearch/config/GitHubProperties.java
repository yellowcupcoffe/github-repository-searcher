package com.example.githubsearch.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed view of the "github.*" section of application.yml.
 * Spring fills the constructor arguments from the configuration (and environment variables).
 */
@ConfigurationProperties(prefix = "github")
public record GitHubProperties(
        String baseUrl,
        String token,
        Duration connectTimeout,
        Duration readTimeout) {

    public boolean hasToken() {
        return token != null && !token.isBlank();
    }
}
