package io.github.peterberghuis.auth.security;

import io.github.peterberghuis.auth.service.AuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final List<OAuth2UserInfoExtractor> extractors;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        Map<String, Object> attributes = oAuth2User.getAttributes();

        OAuth2AuthenticationToken authToken = (OAuth2AuthenticationToken) authentication;
        String providerId = authToken.getAuthorizedClientRegistrationId();

        OAuth2UserInfoExtractor extractor = extractors.stream()
                .filter(e -> e.getProvider().equals(providerId))
                .findFirst()
                .orElseThrow(() -> new ServletException("Provider not supported: " + providerId));

        String email = extractor.getEmail(attributes);
        String name = extractor.getName(attributes);
        String sub = extractor.getSub(attributes);

        // Provision the user in our database
        authService.loginOrRegisterOAuth2User(email, name, sub, providerId);

        // Generate a temporary one-time code for the frontend to exchange for tokens
        String code = authService.generateOAuth2Code(email);

        // Redirect back to the frontend with the code
        String redirectUrl = frontendUrl + "/oauth2-success?code=" + code;
        response.sendRedirect(redirectUrl);
    }
}
