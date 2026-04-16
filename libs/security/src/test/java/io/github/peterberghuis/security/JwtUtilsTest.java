package io.github.peterberghuis.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilsTest {

    @Test
    void testSigningKeyWithTooShortSecret() {
        JwtUtils jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "secret", "too-short");

        // This is expected to throw an WeakKeyException (or similar from JJWT)
        // when getSigningKey is called via generateToken
        assertThrows(Exception.class, () -> {
            jwtUtils.generateToken(UUID.randomUUID(), "user", Collections.emptyList());
        });
    }

    @Test
    void testSigningKeyWithAdequateSecret() {
        JwtUtils jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "secret", "this-is-a-very-long-secret-key-that-is-at-least-32-bytes");
        ReflectionTestUtils.setField(jwtUtils, "jwtExpiration", 3600000L);

        String token = jwtUtils.generateToken(UUID.randomUUID(), "user", Collections.emptyList());
        assertNotNull(token);
    }

    @Test
    void testExpiredToken() {
        JwtUtils jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "secret", "this-is-a-very-long-secret-key-that-is-at-least-32-bytes");
        // Set a negative expiration time to make the token immediately expired
        ReflectionTestUtils.setField(jwtUtils, "jwtExpiration", -1000L);

        String token = jwtUtils.generateToken(UUID.randomUUID(), "user", Collections.emptyList());
        assertNotNull(token);

        boolean isValid = jwtUtils.validateToken(token);
        assertFalse(isValid);
    }

    @Test
    void testExtractUserId() {
        JwtUtils jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "secret", "this-is-a-very-long-secret-key-that-is-at-least-32-bytes");
        ReflectionTestUtils.setField(jwtUtils, "jwtExpiration", 3600000L);

        UUID userId = UUID.randomUUID();
        String token = jwtUtils.generateToken(userId, "user@example.com", Collections.emptyList());
        assertNotNull(token);

        String extractedUserId = jwtUtils.getUserIdFromToken(token);
        assertEquals(userId.toString(), extractedUserId);

        String extractedUsername = jwtUtils.getUsernameFromToken(token);
        assertEquals("user@example.com", extractedUsername);
    }
}
