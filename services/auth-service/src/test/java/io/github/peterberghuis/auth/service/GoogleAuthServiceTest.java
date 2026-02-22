package io.github.peterberghuis.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.peterberghuis.auth.exception.GoogleAuthException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private GoogleAuthService googleAuthService;

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

    @Test
    void testExchangeCode_ShouldIncludeErrorBody_WhenGoogleReturnsError() {
        // Arrange
        String code = "invalid_code";
        String errorBody = "{\"error\": \"invalid_grant\", \"error_description\": \"Bad Request\"}";

        HttpClientErrorException exception = new HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                errorBody.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );

        when(restTemplate.postForEntity(any(String.class), any(), eq(Map.class)))
                .thenThrow(exception);

        // Act & Assert
        GoogleAuthException googleAuthException = assertThrows(GoogleAuthException.class, () -> {
            googleAuthService.exchangeCode(code);
        });

        assertTrue(googleAuthException.getMessage().contains("Error during Google code exchange: " + errorBody));
    }
}
