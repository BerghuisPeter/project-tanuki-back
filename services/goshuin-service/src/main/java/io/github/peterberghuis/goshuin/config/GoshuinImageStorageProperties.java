package io.github.peterberghuis.goshuin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "gcp.storage.goshuin")
public class GoshuinImageStorageProperties {
    private List<String> allowedContentTypes;
    private long maxSizeBytes;
}
