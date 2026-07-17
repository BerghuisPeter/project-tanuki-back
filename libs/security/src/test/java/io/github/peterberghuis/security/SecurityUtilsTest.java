package io.github.peterberghuis.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityUtilsTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testGetUserIdFromContext_Success() {
        UUID expectedId = UUID.randomUUID();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "test@test.com",
                expectedId.toString(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        UUID actualId = SecurityUtils.getUserIdFromContext();

        assertEquals(expectedId, actualId);
    }

    @Test
    void testGetUserIdFromContext_NoAuthentication() {
        SecurityContextHolder.clearContext();

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, SecurityUtils::getUserIdFromContext);
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("User is not authenticated", exception.getReason());
    }

    @Test
    void testGetUserIdFromContext_AnonymousUser() {
        AnonymousAuthenticationToken auth = new AnonymousAuthenticationToken(
                "key",
                "anonymousUser",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, SecurityUtils::getUserIdFromContext);
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("User is not authenticated", exception.getReason());
    }

    @Test
    void testGetUserIdFromContext_NoUserIdInContext() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "test@test.com",
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, SecurityUtils::getUserIdFromContext);
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("User ID not found in security context", exception.getReason());
    }
}
