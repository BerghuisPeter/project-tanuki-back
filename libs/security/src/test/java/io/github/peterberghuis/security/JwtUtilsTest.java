package io.github.peterberghuis.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtUtilsTest {

    @Test
    void testSigningKeyWithTooShortSecret() {
        JwtUtils jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "secret", "too-short");

        // This is expected to throw an WeakKeyException (or similar from JJWT)
        // when getSigningKey is called via generateToken
        assertThrows(Exception.class, () -> {
            jwtUtils.generateToken("user", Collections.emptyList());
        });
    }

    @Test
    void testSigningKeyWithAdequateSecret() {
        JwtUtils jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "secret", "this-is-a-very-long-secret-key-that-is-at-least-32-bytes");
        ReflectionTestUtils.setField(jwtUtils, "jwtExpiration", 3600000L);

        String token = jwtUtils.generateToken("user", Collections.emptyList());
        assertNotNull(token);
    }
}
