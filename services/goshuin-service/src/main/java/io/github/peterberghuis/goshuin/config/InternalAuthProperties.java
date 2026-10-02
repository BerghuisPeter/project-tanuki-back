package io.github.peterberghuis.goshuin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Protects {@code /internal/**} with a Google-signed OIDC identity token (ENRICHMENT.md, section 5.5).
 * Enabled unless switched off, so a deployment that forgets to configure it fails to start instead of
 * exposing the endpoints. The local profile switches it off.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.internal-auth")
public class InternalAuthProperties {

    private boolean enabled = true;

    /**
     * Expected {@code aud} claim: the URL of this service, as the caller requested the token for it.
     */
    private String audience;

    /**
     * Email of the one service account (the enrichment worker's) allowed to call {@code /internal/**}.
     */
    private String allowedEmail;
}
