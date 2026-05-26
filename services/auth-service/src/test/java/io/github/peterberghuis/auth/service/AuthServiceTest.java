package io.github.peterberghuis.auth.service;

import io.github.peterberghuis.auth.client.ProfileClient;
import io.github.peterberghuis.auth.dto.*;
import io.github.peterberghuis.auth.entity.RefreshToken;
import io.github.peterberghuis.auth.entity.User;
import io.github.peterberghuis.auth.entity.UserAuthProvider;
import io.github.peterberghuis.auth.entity.UserStatus;
import io.github.peterberghuis.auth.repository.RefreshTokenRepository;
import io.github.peterberghuis.auth.repository.UserAuthProviderRepository;
import io.github.peterberghuis.auth.repository.UserRepository;
import io.github.peterberghuis.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserAuthProviderRepository userAuthProviderRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private ProfileClient profileClient;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshExpiration", 604800000L);
    }

    private String hashToken(String token) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hashing refresh token", e);
        }
    }

    @Test
    void login_ShouldDeleteOldRefreshToken_WhenSuccessful() {
        // Arrange
        String email = "test@example.com";
        String password = "password";
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash("hashed_password");
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(java.time.LocalDateTime.now());
        user.setRoles(java.util.Set.of(io.github.peterberghuis.auth.entity.UserRole.USER));

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(email);
        loginRequest.setPassword(password);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(password, user.getPasswordHash())).thenReturn(true);
        when(jwtUtils.generateToken(any(UUID.class), anyString(), any())).thenReturn("access_token");
        when(jwtUtils.generateRefreshToken(anyString())).thenReturn("refresh_token");
        when(profileClient.getInternalProfile(any(UUID.class))).thenReturn(new UserProfile());

        // Act
        AuthResponse response = authService.login(loginRequest);

        // Assert
        assertNotNull(response);
        assertEquals("access_token", response.getAccessToken());
        assertEquals("refresh_token", response.getRefreshToken());
        assertNotNull(response.getUser());
        assertEquals(email, response.getUser().getEmail());

        // Verify that old refresh tokens are upserted
        String hashedToken = hashToken("refresh_token");
        verify(refreshTokenRepository).upsertRefreshToken(any(UUID.class), eq(hashedToken), eq(user.getId()), any());
    }

    @Test
    void register_ShouldSaveUserAndLocalAuthProvider() {
        // Arrange
        String email = "newuser@example.com";
        String password = "password";
        String locale = "en-US";
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setEmail(email);
        registerRequest.setPassword(password);
        registerRequest.setLocale(locale);

        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(password)).thenReturn("hashed_password");
        when(jwtUtils.generateToken(any(UUID.class), anyString(), any())).thenReturn("access_token");
        when(jwtUtils.generateRefreshToken(anyString())).thenReturn("refresh_token");
        when(profileClient.createInternalProfile(any(UUID.class), any(UserProfile.class))).thenReturn(new UserProfile());

        // Mock userRepository.save to set ID and createdAt which are normally set by @PrePersist
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            user.setCreatedAt(java.time.LocalDateTime.now());
            return user;
        });

        // Act
        AuthResponse response = authService.register(registerRequest);

        // Assert
        assertNotNull(response);
        verify(userRepository).save(any(User.class));
        verify(userAuthProviderRepository).save(argThat(provider ->
                provider.getProvider().equals("local") &&
                        provider.getProviderUserId().equals(email)
        ));
    }

    @Test
    void logout_ShouldDeleteRefreshToken() {
        // Arrange
        String email = "test@example.com";
        User user = new User();
        user.setEmail(email);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        // Act
        authService.logout(email);

        // Assert
        verify(refreshTokenRepository).deleteByUser(user);
        verify(refreshTokenRepository).flush();
    }

    @Test
    void refresh_ShouldReturnNewRefreshTokenAndInvalidateOldOne() {
        // Arrange
        String oldTokenString = "old_refresh_token";
        String hashedOldToken = hashToken(oldTokenString);
        String newTokenString = "new_refresh_token";
        String hashedNewToken = hashToken(newTokenString);
        String email = "test@example.com";

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(java.time.LocalDateTime.now());
        user.setRoles(java.util.Set.of(io.github.peterberghuis.auth.entity.UserRole.USER));

        RefreshToken oldToken = new RefreshToken();
        oldToken.setToken(hashedOldToken);
        oldToken.setUser(user);
        oldToken.setExpiryDate(java.time.Instant.now().plusSeconds(600));

        RefreshRequest refreshRequest = new RefreshRequest();
        refreshRequest.setRefreshToken(oldTokenString);

        when(refreshTokenRepository.findByToken(hashedOldToken)).thenReturn(Optional.of(oldToken));
        when(jwtUtils.generateToken(any(UUID.class), anyString(), any())).thenReturn("new_access_token");
        when(jwtUtils.generateRefreshToken(email)).thenReturn(newTokenString);

        // Act
        AuthResponse response = authService.refresh(refreshRequest);

        // Assert
        assertNotNull(response);
        assertEquals("new_access_token", response.getAccessToken());
        assertEquals(newTokenString, response.getRefreshToken());
        assertNotNull(response.getUser());
        assertEquals(email, response.getUser().getEmail());

        // Verify that tokens for user are upserted
        verify(refreshTokenRepository).upsertRefreshToken(any(UUID.class), eq(hashedNewToken), eq(user.getId()), any());
    }

    @Test
    void exchangeTempLoginToken_ShouldReturnAuthResponse_WhenTokenValid() {
        // Arrange
        String token = "valid_token";
        String email = "test@example.com";

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(java.time.LocalDateTime.now());
        user.setRoles(java.util.Set.of(io.github.peterberghuis.auth.entity.UserRole.USER));

        when(jwtUtils.validateToken(token)).thenReturn(true);
        when(jwtUtils.getPurposeFromToken(token)).thenReturn("oauth2_exchange");
        when(jwtUtils.getUsernameFromToken(token)).thenReturn(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(jwtUtils.generateToken(any(UUID.class), anyString(), any())).thenReturn("access_token");
        when(jwtUtils.generateRefreshToken(anyString())).thenReturn("refresh_token");
        when(profileClient.getInternalProfile(any(UUID.class))).thenReturn(new UserProfile());

        // Act
        AuthResponse response = authService.exchangeTempLoginToken(token);

        // Assert
        assertNotNull(response);
        assertEquals("access_token", response.getAccessToken());
    }

    @Test
    void exchangeTempLoginToken_ShouldThrowException_WhenTokenInvalid() {
        // Arrange
        String token = "invalid_token";
        when(jwtUtils.validateToken(token)).thenReturn(false);

        // Act & Assert
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.authentication.BadCredentialsException.class, () -> {
            authService.exchangeTempLoginToken(token);
        });
    }

    @Test
    void exchangeTempLoginToken_ShouldThrowException_WhenTokenPurposeInvalid() {
        // Arrange
        String token = "invalid_purpose_token";
        when(jwtUtils.validateToken(token)).thenReturn(true);
        when(jwtUtils.getPurposeFromToken(token)).thenReturn("invalid_purpose");

        // Act & Assert
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.authentication.BadCredentialsException.class, () -> {
            authService.exchangeTempLoginToken(token);
        });
    }

    @Test
    void loginOrRegisterOAuth2User_ShouldCreateNewUser_WhenUserDoesNotExist() {
        // Arrange
        String email = "google-user@example.com";
        String name = "Google User";
        String sub = "google-sub-123";

        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            user.setCreatedAt(java.time.LocalDateTime.now());
            user.setRoles(java.util.Set.of(io.github.peterberghuis.auth.entity.UserRole.USER));
            return user;
        });
        when(userAuthProviderRepository.findByProviderAndProviderUserId("google", sub)).thenReturn(Optional.empty());
        when(userAuthProviderRepository.save(any(UserAuthProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtUtils.generateToken(any(UUID.class), anyString(), any())).thenReturn("access_token");
        when(jwtUtils.generateRefreshToken(anyString())).thenReturn("refresh_token");
        when(profileClient.createInternalProfile(any(UUID.class), isNull())).thenReturn(new UserProfile());

        // Act
        AuthResponse response = authService.loginOrRegisterOAuth2User(email, name, sub, "google");

        // Assert
        assertNotNull(response);
        assertEquals("access_token", response.getAccessToken());
        verify(userRepository).save(any(User.class));
        verify(userAuthProviderRepository).save(any(UserAuthProvider.class));
    }

    @Test
    void loginOrRegisterOAuth2User_ShouldReturnExistingUser_WhenUserExists() {
        // Arrange
        String email = "existing@example.com";
        String name = "Existing User";
        String sub = "google-sub-456";
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(java.time.LocalDateTime.now());
        user.setRoles(java.util.Set.of(io.github.peterberghuis.auth.entity.UserRole.USER));

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(userAuthProviderRepository.findByProviderAndProviderUserId("google", sub))
                .thenReturn(Optional.of(new UserAuthProvider(UUID.randomUUID(), user, "google", sub)));
        when(jwtUtils.generateToken(any(UUID.class), anyString(), any())).thenReturn("access_token");
        when(jwtUtils.generateRefreshToken(anyString())).thenReturn("refresh_token");
        when(profileClient.createInternalProfile(any(UUID.class), isNull())).thenReturn(new UserProfile());

        // Act
        AuthResponse response = authService.loginOrRegisterOAuth2User(email, name, sub, "google");

        // Assert
        assertNotNull(response);
        assertEquals("access_token", response.getAccessToken());
        verify(userRepository, never()).save(any(User.class));
    }
}
