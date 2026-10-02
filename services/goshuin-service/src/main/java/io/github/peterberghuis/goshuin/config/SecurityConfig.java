package io.github.peterberghuis.goshuin.config;

import com.google.auth.oauth2.TokenVerifier;
import io.github.peterberghuis.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String GOOGLE_ISSUER = "https://accounts.google.com";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final InternalAuthProperties internalAuthProperties;

    private static void requireConfigured(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("app.internal-auth." + name + " must be set when app.internal-auth.enabled=true");
        }
    }

    /**
     * {@code /internal/**} is for the enrichment worker only: it is guarded by the worker's OIDC token, never by the
     * user JWT. Left open when {@code app.internal-auth.enabled=false} (local profile).
     */
    @Bean
    @Order(1)
    public SecurityFilterChain internalSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (internalAuthProperties.isEnabled()) {
            requireConfigured("audience", internalAuthProperties.getAudience());
            requireConfigured("allowed-email", internalAuthProperties.getAllowedEmail());
            TokenVerifier tokenVerifier = TokenVerifier.newBuilder()
                    .setAudience(internalAuthProperties.getAudience())
                    .setIssuer(GOOGLE_ISSUER)
                    .build();
            http
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .addFilterBefore(new InternalAuthFilter(tokenVerifier, internalAuthProperties.getAllowedEmail()),
                            UsernamePasswordAuthenticationFilter.class);
        } else {
            log.warn("app.internal-auth.enabled=false: /internal/** is open to anyone. Never use this outside local development.");
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                res.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/temples", "/api/v1/goshuin").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
