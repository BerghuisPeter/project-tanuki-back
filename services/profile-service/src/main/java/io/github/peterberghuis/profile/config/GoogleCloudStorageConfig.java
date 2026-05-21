package io.github.peterberghuis.profile.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class GoogleCloudStorageConfig {

    @Value("${gcp.storage.credentials-json:}")
    private String credentialsJson;

    @Bean
    public Storage storage() throws IOException {
        // 1. Check for raw JSON content (most flexible for CI/CD and Docker)
        if (credentialsJson != null && !credentialsJson.trim().isEmpty()) {
            return StorageOptions.newBuilder()
                    .setCredentials(GoogleCredentials.fromStream(
                            new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8))))
                    .build()
                    .getService();
        }

        // 2. Fallback to default (Workload Identity / GAE / GCE) for production
        return StorageOptions.getDefaultInstance().getService();
    }
}
