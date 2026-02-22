package io.github.peterberghuis.auth.controller;


import io.github.peterberghuis.auth.api.AuthControllerApi;
import io.github.peterberghuis.auth.dto.*;
import io.github.peterberghuis.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequiredArgsConstructor
public class AuthController implements AuthControllerApi {

    private final AuthService authService;

    @Override
    public ResponseEntity<AuthResponse> register(RegisterRequest registerRequest) {
        return ResponseEntity.ok(authService.register(registerRequest));
    }

    @Override
    public ResponseEntity<AuthResponse> login(LoginRequest loginRequest) {
        return ResponseEntity.ok(authService.login(loginRequest));
    }

    @Override
    public ResponseEntity<AuthResponse> googleLogin(GoogleLoginRequest googleLoginRequest) {
        return ResponseEntity.ok(authService.googleLogin(googleLoginRequest));
    }

    @Override
    public ResponseEntity<AuthResponse> refresh(RefreshRequest refreshRequest) {
        return ResponseEntity.ok(authService.refresh(refreshRequest));
    }

    @Override
    public ResponseEntity<UserResponse> me() {
        String email = Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getName();
        return ResponseEntity.ok(authService.me(email));
    }

    @Override
    public ResponseEntity<Void> logout() {
        String email = Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getName();
        authService.logout(email);
        return ResponseEntity.noContent().build();
    }
}
