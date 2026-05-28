package io.github.peterberghuis.auth.service;

import io.github.peterberghuis.auth.client.ProfileClient;
import io.github.peterberghuis.auth.dto.*;
import io.github.peterberghuis.auth.entity.*;
import io.github.peterberghuis.auth.entity.UserRole;
import io.github.peterberghuis.auth.entity.UserStatus;
import io.github.peterberghuis.auth.exception.EmailAlreadyInUseException;
import io.github.peterberghuis.auth.repository.RefreshTokenRepository;
import io.github.peterberghuis.auth.repository.UserAuthProviderRepository;
import io.github.peterberghuis.auth.repository.UserRepository;
import io.github.peterberghuis.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserAuthProviderRepository userAuthProviderRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final ProfileClient profileClient;

    @Value("${jwt.refresh-expiration}")
    private Long refreshExpiration;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadCredentialsException("User account is " + user.getStatus());
        }

        UserProfile userProfile = profileClient.getInternalProfile(user.getId());
        return createAuthResponse(user, userProfile);
    }

    @Transactional
    public AuthResponse loginOrRegisterOAuth2User(String email, String name, String sub, String provider) {
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setPasswordHash(null);
            newUser.setStatus(UserStatus.ACTIVE);
            newUser.setRoles(Set.of(UserRole.USER));
            return userRepository.save(newUser);
        });

        userAuthProviderRepository.findByProviderAndProviderUserId(provider, sub)
                .orElseGet(() -> {
                    UserAuthProvider authProvider = new UserAuthProvider();
                    authProvider.setUser(user);
                    authProvider.setProvider(provider);
                    authProvider.setProviderUserId(sub);
                    return userAuthProviderRepository.save(authProvider);
                });

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadCredentialsException("User account is " + user.getStatus());
        }

        UserProfile createdProfile = profileClient.createInternalProfile(user.getId(), new UserProfile());
        return createAuthResponse(user, createdProfile);
    }

    public String generateOAuth2TempLoginToken(String email) {
        return jwtUtils.generateTempLoginToken(email);
    }

    @Transactional
    public AuthResponse exchangeTempLoginToken(String token) {
        if (!jwtUtils.validateToken(token)) {
            throw new BadCredentialsException("Invalid or expired token");
        }

        String purpose = jwtUtils.getPurposeFromToken(token);
        if (!"oauth2_exchange".equals(purpose)) {
            throw new BadCredentialsException("Invalid token purpose");
        }

        String email = jwtUtils.getUsernameFromToken(token);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadCredentialsException("User account is " + user.getStatus());
        }

        UserProfile userProfile = profileClient.getInternalProfile(user.getId());
        return createAuthResponse(user, userProfile);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new EmailAlreadyInUseException("Email already in use");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(UserRole.USER));

        userRepository.save(user);

        UserAuthProvider localProvider = new UserAuthProvider();
        localProvider.setUser(user);
        localProvider.setProvider("local");
        localProvider.setProviderUserId(user.getEmail());
        userAuthProviderRepository.save(localProvider);

        UserProfile profileData = new UserProfile();
        profileData.setLocale(request.getLocale());
        UserProfile createdProfile = profileClient.createInternalProfile(user.getId(), profileData);

        return createAuthResponse(user, createdProfile);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String requestRefreshToken = request.getRefreshToken();
        String hashedToken = hashToken(requestRefreshToken);

        RefreshToken token = refreshTokenRepository.findByToken(hashedToken)
                .map(this::verifyExpiration)
                .orElseThrow(() -> new BadCredentialsException("Refresh token is not in database!"));

        User user = token.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadCredentialsException("User account is " + user.getStatus());
        }
        return createAuthResponse(user, null);
    }

    @Transactional(readOnly = true)
    public UserResponse me(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        UserProfile userProfile = profileClient.getInternalProfile(user.getId());
        return toUserResponse(user, userProfile);
    }

    @Transactional
    public void logout(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("User not found"));
        refreshTokenRepository.deleteByUser(user);
        refreshTokenRepository.flush();
    }

    private AuthResponse createAuthResponse(User user, UserProfile userProfile) {
        String accessToken = generateAccessToken(user);
        String refreshToken = createRefreshToken(user).getToken();
        AuthResponse response = new AuthResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setUser(toUserResponse(user, userProfile));
        return response;
    }

    private UserResponse toUserResponse(User user, UserProfile userProfile) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setEmail(user.getEmail());
        response.setCreatedAt(user.getCreatedAt().atOffset(java.time.ZoneOffset.UTC));
        response.setRoles(user.getRoles().stream()
                .map(role -> io.github.peterberghuis.auth.dto.UserRole.fromValue(role.name()))
                .toList());

        if (userProfile != null) {
            response.setProfile(userProfile);
        }

        return response;
    }

    private String generateAccessToken(User user) {
        var authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.name()))
                .toList();

        return jwtUtils.generateToken(user.getId(), user.getEmail(), authorities);
    }

    private RefreshToken createRefreshToken(User user) {
        String rawRefreshToken = jwtUtils.generateRefreshToken(user.getEmail());
        String hashedToken = hashToken(rawRefreshToken);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(UUID.randomUUID());
        refreshToken.setUser(user);
        refreshToken.setExpiryDate(Instant.now().plusMillis(refreshExpiration));
        refreshToken.setToken(hashedToken);

        refreshTokenRepository.upsertRefreshToken(
                refreshToken.getId(),
                refreshToken.getToken(),
                user.getId(),
                refreshToken.getExpiryDate()
        );

        // Return a temporary token object with the raw token to send back to the client
        RefreshToken responseToken = new RefreshToken();
        responseToken.setToken(rawRefreshToken);
        return responseToken;
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hashing refresh token", e);
        }
    }

    private RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(token);
            throw new BadCredentialsException("Refresh token was expired. Please make a new signin request");
        }
        return token;
    }
}

