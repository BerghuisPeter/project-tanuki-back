package io.github.peterberghuis.auth.service;

import io.github.peterberghuis.auth.dto.AuthResponse;
import io.github.peterberghuis.auth.dto.LoginRequest;
import io.github.peterberghuis.auth.dto.RefreshRequest;
import io.github.peterberghuis.auth.entity.RefreshToken;
import io.github.peterberghuis.auth.entity.User;
import io.github.peterberghuis.auth.entity.UserStatus;
import io.github.peterberghuis.auth.repository.RefreshTokenRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

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

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("refresh_token");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(refreshToken);

        // Act
        AuthResponse response = authService.login(loginRequest);

        // Assert
        assertNotNull(response);
        assertEquals("access_token", response.getAccessToken());
        assertEquals("refresh_token", response.getRefreshToken());
        assertNotNull(response.getUser());
        assertEquals(email, response.getUser().getEmail());

        // Verify that old refresh tokens are deleted
        verify(refreshTokenRepository).deleteByUser(user);
        verify(refreshTokenRepository).flush();
        verify(refreshTokenRepository).save(any(RefreshToken.class));
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

        RefreshToken newToken = new RefreshToken();
        newToken.setToken(newTokenString);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(newToken);

        // Act
        AuthResponse response = authService.refresh(refreshRequest);

        // Assert
        assertNotNull(response);
        assertEquals("new_access_token", response.getAccessToken());
        assertEquals(newTokenString, response.getRefreshToken());
        assertNotNull(response.getUser());
        assertEquals(email, response.getUser().getEmail());

        // Verify that old tokens for user are deleted
        verify(refreshTokenRepository).deleteByUser(user);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }
}
