package io.github.peterberghuis.auth.security;

import java.util.Map;

public interface OAuth2UserInfoExtractor {
    String getEmail(Map<String, Object> attributes);

    String getName(Map<String, Object> attributes);

    String getSub(Map<String, Object> attributes);

    String getProvider(); // "google", "facebook", etc.
}
