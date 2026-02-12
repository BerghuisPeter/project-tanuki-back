package io.github.peterberghuis.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Setter
@Getter
@Configuration
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {
    private String allowedOrigins;

    public List<String> getAllowedOriginsList() {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(allowedOrigins.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
