package io.github.peterberghuis.profile.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "gcp.storage")
public class GcpStorageProperties {

    private String bucketName;
    private String credentialsJson;
    private Avatar avatar = new Avatar();

    @Data
    public static class Avatar {
        private List<String> allowedContentTypes;
        private long maxSizeBytes;
    }
}
