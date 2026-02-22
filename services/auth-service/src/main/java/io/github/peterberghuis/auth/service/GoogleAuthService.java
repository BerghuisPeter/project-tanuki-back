package io.github.peterberghuis.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GoogleAuthService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Value("${app.google.client-id}")
    private String clientId;
    @Value("${app.google.client-secret}")
    private String clientSecret;
    @Value("${app.google.redirect-uri}")
    private String redirectUri;

    public GoogleUserInfo exchangeCode(String code) {
        String url = "https://oauth2.googleapis.com/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("redirect_uri", redirectUri);
        params.add("grant_type", "authorization_code");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            String idToken = (String) response.getBody().get("id_token");
            if (idToken == null) {
                throw new RuntimeException("No id_token found in Google response");
            }
            return decodeIdToken(idToken);
        }
        throw new RuntimeException("Failed to exchange code for token: " + response.getStatusCode());
    }

    private GoogleUserInfo decodeIdToken(String idToken) {
        try {
            // JWT format: header.payload.signature
            String[] parts = idToken.split("\\.");
            if (parts.length < 2) {
                throw new RuntimeException("Invalid id_token format");
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            return objectMapper.readValue(payload, GoogleUserInfo.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode id_token", e);
        }
    }

    @Data
    public static class GoogleUserInfo {
        private String sub;
        private String email;
        private String name;
        private String picture;
    }
}
