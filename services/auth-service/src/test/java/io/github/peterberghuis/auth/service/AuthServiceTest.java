package io.github.peterberghuis.auth.service;

import io.github.peterberghuis.auth.dto.AuthResponse;
import io.github.peterberghuis.auth.dto.GoogleLoginRequest;
import io.github.peterberghuis.auth.dto.LoginRequest;
import io.github.peterberghuis.auth.dto.RefreshRequest;
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
    private GoogleAuthService googleAuthService;

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
    void googleLogin_ShouldCreateUser_WhenNotExists() {
        // Arrange
        String code = "google_code";
        String googleSub = "google_sub";
        String email = "google@example.com";

        GoogleLoginRequest request = new GoogleLoginRequest();
        request.setCode(code);

        GoogleAuthService.GoogleUserInfo userInfo = new GoogleAuthService.GoogleUserInfo();
        userInfo.setSub(googleSub);
        userInfo.setEmail(email);

        when(googleAuthService.exchangeCode(code)).thenReturn(userInfo);
        when(userAuthProviderRepository.findByProviderAndProviderUserId("GOOGLE", googleSub)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            user.setCreatedAt(java.time.LocalDateTime.now());
            return user;
        });
        when(jwtUtils.generateToken(anyString(), any())).thenReturn("access_token");
        when(jwtUtils.generateRefreshToken(anyString())).thenReturn("refresh_token");

        // Act
        AuthResponse response = authService.googleLogin(request);

        // Assert
        assertNotNull(response);
        assertEquals(email, response.getUser().getEmail());
        verify(userRepository).save(any(User.class));
        verify(userAuthProviderRepository).save(any(UserAuthProvider.class));
    }

    @Test
    void googleLogin_ShouldLinkExistingUser_WhenNotLinked() {
        // Arrange
        String code = "google_code";
        String googleSub = "google_sub";
        String email = "existing@example.com";

        GoogleLoginRequest request = new GoogleLoginRequest();
        request.setCode(code);

        GoogleAuthService.GoogleUserInfo userInfo = new GoogleAuthService.GoogleUserInfo();
        userInfo.setSub(googleSub);
        userInfo.setEmail(email);

        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());
        existingUser.setEmail(email);
        existingUser.setStatus(UserStatus.ACTIVE);
        existingUser.setCreatedAt(java.time.LocalDateTime.now());
        existingUser.setRoles(java.util.Set.of(io.github.peterberghuis.auth.entity.UserRole.USER));

        when(googleAuthService.exchangeCode(code)).thenReturn(userInfo);
        when(userAuthProviderRepository.findByProviderAndProviderUserId("GOOGLE", googleSub)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(existingUser));
        when(jwtUtils.generateToken(anyString(), any())).thenReturn("access_token");
        when(jwtUtils.generateRefreshToken(anyString())).thenReturn("refresh_token");

        // Act
        AuthResponse response = authService.googleLogin(request);

        // Assert
        assertNotNull(response);
        verify(userAuthProviderRepository).save(any(UserAuthProvider.class));
        verify(userRepository, org.mockito.Mockito.never()).save(any(User.class));
    }
}
