package io.github.peterberghuis.gcp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "gcp.storage")
public class GcpStorageProperties {
    private String bucketName;
    private String credentialsJson;
}
