package io.github.peterberghuis.goshuin.config;

import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.auth.oauth2.TokenVerifier;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InternalAuthFilterTest {

    private static final String WORKER_EMAIL = "enrichment-worker@tanuki-dev.iam.gserviceaccount.com";

    private final TokenVerifier tokenVerifier = mock(TokenVerifier.class);
    private final FilterChain chain = mock(FilterChain.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/temples/1");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private InternalAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new InternalAuthFilter(tokenVerifier, WORKER_EMAIL);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void noAuthorizationHeader_Returns401() throws Exception {
        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void nonBearerAuthorizationHeader_Returns401() throws Exception {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void tokenFailingVerification_Returns401() throws Exception {
        request.addHeader("Authorization", "Bearer bad-token");
        when(tokenVerifier.verify("bad-token")).thenThrow(new TokenVerifier.VerificationException("expired"));

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertEquals("application/json", response.getContentType());
        verify(chain, never()).doFilter(any(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void malformedToken_Returns401() throws Exception {
        request.addHeader("Authorization", "Bearer not-a-token");
        when(tokenVerifier.verify("not-a-token")).thenThrow(new IllegalArgumentException());

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertEquals("application/json", response.getContentType());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void validTokenOfAnotherServiceAccount_Returns403() throws Exception {
        request.addHeader("Authorization", "Bearer token");
        when(tokenVerifier.verify("token")).thenReturn(token("someone-else@tanuki-dev.iam.gserviceaccount.com", true));

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void validTokenWithUnverifiedEmail_Returns403() throws Exception {
        request.addHeader("Authorization", "Bearer token");
        when(tokenVerifier.verify("token")).thenReturn(token(WORKER_EMAIL, false));

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void validTokenWithoutEmail_Returns403() throws Exception {
        request.addHeader("Authorization", "Bearer token");
        JsonWebSignature jws = new JsonWebSignature(new JsonWebSignature.Header(), new JsonWebSignature.Payload(), new byte[0], new byte[0]);
        jws.getPayload().set("email_verified", true);
        when(tokenVerifier.verify("token")).thenReturn(jws);

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void validTokenOfWorker_ContinuesChainAsSystem() throws Exception {
        request.addHeader("Authorization", "Bearer token");
        when(tokenVerifier.verify("token")).thenReturn(token(WORKER_EMAIL.toUpperCase(), true));

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(request, response);
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertEquals(WORKER_EMAIL, authentication.getPrincipal());
        assertEquals("ROLE_SYSTEM", authentication.getAuthorities().iterator().next().getAuthority());
    }

    private JsonWebSignature token(String email, boolean emailVerified) {
        JsonWebSignature jws = new JsonWebSignature(new JsonWebSignature.Header(), new JsonWebSignature.Payload(), new byte[0], new byte[0]);
        jws.getPayload().set("email", email);
        jws.getPayload().set("email_verified", emailVerified);
        return jws;
    }
}
