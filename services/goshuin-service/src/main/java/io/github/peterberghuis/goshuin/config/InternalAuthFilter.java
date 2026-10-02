package io.github.peterberghuis.goshuin.config;

import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.auth.oauth2.TokenVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Accepts a request only when it carries a valid Google-signed OIDC identity token (signature, issuer, expiry and
 * audience are checked by the {@link TokenVerifier}) for the one allowed service account.
 * Not a Spring bean on purpose: it is added to the {@code /internal/**} filter chain only.
 */
@Slf4j
@RequiredArgsConstructor
public class InternalAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenVerifier tokenVerifier;
    private final String allowedEmail;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            reject(response, HttpStatus.UNAUTHORIZED, "Missing bearer token");
            return;
        }

        JsonWebSignature.Payload payload;
        try {
            payload = tokenVerifier.verify(header.substring(BEARER_PREFIX.length())).getPayload();
        } catch (TokenVerifier.VerificationException | IllegalArgumentException e) { // the latter: not even a JWT
            log.warn("Rejected internal request to {}: {}", request.getRequestURI(), e.getMessage());
            reject(response, HttpStatus.UNAUTHORIZED, "Invalid token");
            return;
        }

        if (!Boolean.TRUE.equals(payload.get("email_verified"))
                || !(payload.get("email") instanceof String email)
                || !allowedEmail.equalsIgnoreCase(email)) {
            log.warn("Rejected internal request to {}: caller is not the allowed service account", request.getRequestURI());
            reject(response, HttpStatus.FORBIDDEN, "Caller is not allowed");
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                allowedEmail, null, List.of(new SimpleGrantedAuthority("ROLE_SYSTEM"))));
        filterChain.doFilter(request, response);
    }

    // Written directly instead of with sendError: an error dispatch to /error would go through the public chain.
    private void reject(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"status\":%d,\"error\":\"%s\",\"message\":\"%s\"}"
                .formatted(status.value(), status.getReasonPhrase(), message));
    }
}
