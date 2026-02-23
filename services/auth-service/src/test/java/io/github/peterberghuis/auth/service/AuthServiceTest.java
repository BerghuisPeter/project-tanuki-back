package io.github.peterberghuis.auth.service;

import io.github.peterberghuis.auth.dto.AuthResponse;
import io.github.peterberghuis.auth.dto.LoginRequest;
import io.github.peterberghuis.auth.dto.RefreshRequest;
import io.github.peterberghuis.auth.dto.RegisterRequest;
import io.github.peterberghuis.auth.entity.OAuth2Code;
import io.github.peterberghuis.auth.entity.RefreshToken;
import io.github.peterberghuis.auth.entity.User;
import io.github.peterberghuis.auth.entity.UserStatus;
import io.github.peterberghuis.auth.repository.OAuth2CodeRepository;
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

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserAuthProviderRepository userAuthProviderRepository;

    @Mock
    private OAuth2CodeRepository oauth2CodeRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtils jwtUtils;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshExpiration", 604800000L);
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
        when(jwtUtils.generateToken(anyString(), any())).thenReturn("access_token");
        when(jwtUtils.generateRefreshToken(anyString())).thenReturn("refresh_token");

        // Act
        AuthResponse response = authService.login(loginRequest);

        // Assert
        assertNotNull(response);
        assertEquals("access_token", response.getAccessToken());
        assertEquals("refresh_token", response.getRefreshToken());
        assertNotNull(response.getUser());
        assertEquals(email, response.getUser().getEmail());

        // Verify that old refresh tokens are upserted
        verify(refreshTokenRepository).upsertRefreshToken(any(UUID.class), eq("refresh_token"), eq(user.getId()), any());
    }

    @Test
    void register_ShouldSaveUserAndLocalAuthProvider() {
        // Arrange
        String email = "newuser@example.com";
        String password = "password";
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setEmail(email);
        registerRequest.setPassword(password);

        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(password)).thenReturn("hashed_password");
        when(jwtUtils.generateToken(anyString(), any())).thenReturn("access_token");
        when(jwtUtils.generateRefreshToken(anyString())).thenReturn("refresh_token");

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
        String newTokenString = "new_refresh_token";
        String email = "test@example.com";

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(java.time.LocalDateTime.now());
        user.setRoles(java.util.Set.of(io.github.peterberghuis.auth.entity.UserRole.USER));

        RefreshToken oldToken = new RefreshToken();
        oldToken.setToken(oldTokenString);
        oldToken.setUser(user);
        oldToken.setExpiryDate(java.time.Instant.now().plusSeconds(600));

        RefreshRequest refreshRequest = new RefreshRequest();
        refreshRequest.setRefreshToken(oldTokenString);

        when(refreshTokenRepository.findByToken(oldTokenString)).thenReturn(Optional.of(oldToken));
        when(jwtUtils.generateToken(anyString(), any())).thenReturn("new_access_token");
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
        verify(refreshTokenRepository).upsertRefreshToken(any(UUID.class), eq(newTokenString), eq(user.getId()), any());
    }

    @Test
    void exchangeCode_ShouldReturnAuthResponse_WhenCodeValid() {
        // Arrange
        String code = "valid_code";
        String email = "test@example.com";
        OAuth2Code oauth2Code = new OAuth2Code();
        oauth2Code.setCode(code);
        oauth2Code.setEmail(email);
        oauth2Code.setExpiryDate(java.time.Instant.now().plusSeconds(60));

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(java.time.LocalDateTime.now());
        user.setRoles(java.util.Set.of(io.github.peterberghuis.auth.entity.UserRole.USER));

        when(oauth2CodeRepository.findByCode(code)).thenReturn(Optional.of(oauth2Code));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(jwtUtils.generateToken(anyString(), any())).thenReturn("access_token");
        when(jwtUtils.generateRefreshToken(anyString())).thenReturn("refresh_token");

        // Act
        AuthResponse response = authService.exchangeCode(code);

        // Assert
        assertNotNull(response);
        assertEquals("access_token", response.getAccessToken());
        verify(oauth2CodeRepository).delete(oauth2Code);
    }

    @Test
    void exchangeCode_ShouldThrowException_WhenCodeExpired() {
        // Arrange
        String code = "expired_code";
        OAuth2Code oauth2Code = new OAuth2Code();
        oauth2Code.setCode(code);
        oauth2Code.setExpiryDate(java.time.Instant.now().minusSeconds(60));

        when(oauth2CodeRepository.findByCode(code)).thenReturn(Optional.of(oauth2Code));

        // Act & Assert
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.authentication.BadCredentialsException.class, () -> {
            authService.exchangeCode(code);
        });
        verify(oauth2CodeRepository).delete(oauth2Code);
    }

    @Test
    void exchangeCode_ShouldThrowException_WhenCodeNotFound() {
        // Arrange
        String code = "not_found_code";
        when(oauth2CodeRepository.findByCode(code)).thenReturn(Optional.empty());

        // Act & Assert
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.authentication.BadCredentialsException.class, () -> {
            authService.exchangeCode(code);
        });
    }
}
