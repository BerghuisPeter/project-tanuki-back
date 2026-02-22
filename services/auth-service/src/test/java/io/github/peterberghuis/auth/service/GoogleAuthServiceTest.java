package io.github.peterberghuis.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.peterberghuis.auth.exception.GoogleAuthException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class GoogleAuthServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GoogleAuthService googleAuthService = new GoogleAuthService();

    @Test
    void testGoogleUserInfoDeserialization_WithManyProperties() throws Exception {
        String json = "{\"sub\":\"123\", \"email\":\"test@example.com\", \"email_verified\":true, \"name\":\"John Doe\", \"given_name\":\"John\", \"family_name\":\"Doe\", \"picture\":\"http://example.com/pic\", \"locale\":\"en\", \"hd\":\"example.com\", \"iss\":\"https://accounts.google.com\", \"azp\":\"client_id\", \"aud\":\"audience\", \"iat\":1700000000, \"exp\":1700003600, \"at_hash\":\"hash\", \"nonce\":\"nonce\", \"unknown\":\"ignored\"}";

        GoogleAuthService.GoogleUserInfo userInfo = objectMapper.readValue(json, GoogleAuthService.GoogleUserInfo.class);

        assertEquals("123", userInfo.getSub());
        assertEquals("test@example.com", userInfo.getEmail());
        assertTrue(userInfo.isEmailVerified());
        assertEquals("John Doe", userInfo.getName());
        assertEquals("John", userInfo.getGivenName());
        assertEquals("Doe", userInfo.getFamilyName());
        assertEquals("http://example.com/pic", userInfo.getPicture());
        assertEquals("en", userInfo.getLocale());
        assertEquals("example.com", userInfo.getHd());
        assertEquals("https://accounts.google.com", userInfo.getIss());
        assertEquals("client_id", userInfo.getAzp());
        assertEquals("audience", userInfo.getAud());
        assertEquals(1700000000L, userInfo.getIat());
        assertEquals(1700003600L, userInfo.getExp());
        assertEquals("hash", userInfo.getAtHash());
        assertEquals("nonce", userInfo.getNonce());
    }

    @Test
    void testDecodeIdToken_InvalidFormat() {
        String invalidIdToken = "header.payload"; // Missing signature part, but split by . will have 2 parts.
        // Actually split by "." with "header.payload" gives ["header", "payload"] which is length 2.
        // My code checks if parts.length < 2.

        String tooShortIdToken = "headeronly";

        GoogleAuthException exception = assertThrows(GoogleAuthException.class, () -> {
            ReflectionTestUtils.invokeMethod(googleAuthService, "decodeIdToken", tooShortIdToken);
        });

        assertEquals("Invalid id_token format", exception.getMessage());
    }

    @Test
    void testDecodeIdToken_InvalidBase64() {
        String invalidBase64IdToken = "header.invalid_base64!.signature";

        assertThrows(GoogleAuthException.class, () -> {
            ReflectionTestUtils.invokeMethod(googleAuthService, "decodeIdToken", invalidBase64IdToken);
        });
    }
}
