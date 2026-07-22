package io.github.peterberghuis.gcp.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class GoogleCloudStorageConfig {

    private final GcpStorageProperties gcpStorageProperties;

    public GoogleCloudStorageConfig(GcpStorageProperties gcpStorageProperties) {
        this.gcpStorageProperties = gcpStorageProperties;
    }

    @Bean
    public Storage storage() throws IOException {
        String credentialsJson = gcpStorageProperties.getCredentialsJson();
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
