package io.github.peterberghuis.auth.service;

import io.github.peterberghuis.auth.dto.*;
import io.github.peterberghuis.auth.entity.User;
import io.github.peterberghuis.auth.entity.UserRole;
import io.github.peterberghuis.auth.entity.UserStatus;
import io.github.peterberghuis.auth.exception.EmailAlreadyInUseException;
import io.github.peterberghuis.auth.repository.UserRepository;
import io.github.peterberghuis.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadCredentialsException("User account is " + user.getStatus());
        }

        return new AuthResponse(generateToken(user));
    }

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

        return new AuthResponse(generateToken(user));
    }

    public AuthResponse refresh(RefreshRequest request) {
        if (!jwtUtils.validateToken(request.getRefreshToken())) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        String email = jwtUtils.getUsernameFromToken(request.getRefreshToken());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadCredentialsException("User account is " + user.getStatus());
        }

        return new AuthResponse(generateToken(user));
    }

    public UserResponse me(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getRoles()
        );
    }

    private String generateToken(User user) {
        var authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.name()))
                .toList();

        return jwtUtils.generateToken(user.getEmail(), authorities);
    }
}
