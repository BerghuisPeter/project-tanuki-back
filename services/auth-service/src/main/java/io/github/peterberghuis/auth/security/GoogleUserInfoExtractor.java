package io.github.peterberghuis.auth.security;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GoogleUserInfoExtractor implements OAuth2UserInfoExtractor {
    @Override
    public String getEmail(Map<String, Object> attributes) {
        return (String) attributes.get("email");
    }

    @Override
    public String getName(Map<String, Object> attributes) {
        return (String) attributes.get("name");
    }

    @Override
    public String getSub(Map<String, Object> attributes) {
        return (String) attributes.get("sub");
    }

    @Override
    public String getProvider() {
        return "google";
    }
}
