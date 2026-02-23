package io.github.peterberghuis.auth.service;

import io.github.peterberghuis.auth.dto.*;
import io.github.peterberghuis.auth.entity.*;
import io.github.peterberghuis.auth.entity.UserRole;
import io.github.peterberghuis.auth.entity.UserStatus;
import io.github.peterberghuis.auth.exception.EmailAlreadyInUseException;
import io.github.peterberghuis.auth.repository.OAuth2CodeRepository;
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

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserAuthProviderRepository userAuthProviderRepository;
    private final OAuth2CodeRepository oauth2CodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    @Value("${jwt.refresh-expiration}")
    private Long refreshExpiration;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadCredentialsException("User account is " + user.getStatus());
        }

        return createAuthResponse(user);
    }

    @Transactional
    public AuthResponse createAuthResponseForUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadCredentialsException("User account is " + user.getStatus());
        }

        return createAuthResponse(user);
    }

    @Transactional
    public String generateOAuth2Code(String email) {
        String code = UUID.randomUUID().toString();
        OAuth2Code oauth2Code = new OAuth2Code();
        oauth2Code.setCode(code);
        oauth2Code.setEmail(email);
        oauth2Code.setExpiryDate(Instant.now().plusSeconds(300)); // 5 minutes
        oauth2CodeRepository.save(oauth2Code);
        return code;
    }

    @Transactional
    public AuthResponse exchangeCode(String code) {
        OAuth2Code oauth2Code = oauth2CodeRepository.findByCode(code)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired code"));

        if (oauth2Code.getExpiryDate().isBefore(Instant.now())) {
            oauth2CodeRepository.delete(oauth2Code);
            throw new BadCredentialsException("Invalid or expired code");
        }

        String email = oauth2Code.getEmail();
        oauth2CodeRepository.delete(oauth2Code);

        return createAuthResponseForUser(email);
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

        return createAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        RefreshToken token = refreshTokenRepository.findByToken(requestRefreshToken)
                .map(this::verifyExpiration)
                .orElseThrow(() -> new BadCredentialsException("Refresh token is not in database!"));

        User user = token.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadCredentialsException("User account is " + user.getStatus());
        }

        return createAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse me(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        return toUserResponse(user);
    }

    @Transactional
    public void logout(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("User not found"));
        refreshTokenRepository.deleteByUser(user);
        refreshTokenRepository.flush();
    }

    private AuthResponse createAuthResponse(User user) {
        String accessToken = generateAccessToken(user);
        String refreshToken = createRefreshToken(user).getToken();
        AuthResponse response = new AuthResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setUser(toUserResponse(user));
        return response;
    }

    private UserResponse toUserResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setEmail(user.getEmail());
        response.setStatus(io.github.peterberghuis.auth.dto.UserStatus.fromValue(user.getStatus().name()));
        response.setCreatedAt(user.getCreatedAt().atOffset(java.time.ZoneOffset.UTC));
        response.setRoles(user.getRoles().stream()
                .map(role -> io.github.peterberghuis.auth.dto.UserRole.fromValue(role.name()))
                .toList());
        return response;
    }

    private String generateAccessToken(User user) {
        var authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.name()))
                .toList();

        return jwtUtils.generateToken(user.getEmail(), authorities);
    }

    private RefreshToken createRefreshToken(User user) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(UUID.randomUUID());
        refreshToken.setUser(user);
        refreshToken.setExpiryDate(Instant.now().plusMillis(refreshExpiration));
        refreshToken.setToken(jwtUtils.generateRefreshToken(user.getEmail()));

        refreshTokenRepository.upsertRefreshToken(
                refreshToken.getId(),
                refreshToken.getToken(),
                user.getId(),
                refreshToken.getExpiryDate()
        );

        return refreshToken;
    }

    private RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(token);
            throw new BadCredentialsException("Refresh token was expired. Please make a new signin request");
        }
        return token;
    }
}
