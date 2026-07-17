package io.github.peterberghuis.profile.controller;

import io.github.peterberghuis.profile.api.ProfileApi;
import io.github.peterberghuis.profile.dto.UploadUrlResponse;
import io.github.peterberghuis.profile.dto.UserProfile;
import io.github.peterberghuis.profile.service.ProfileService;
import io.github.peterberghuis.profile.validator.UserProfileValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ProfileController implements ProfileApi {

    private final ProfileService profileService;
    private final UserProfileValidator userPreferencesValidator;

    @Override
    public ResponseEntity<UserProfile> getUserProfile() {
        UUID userId = getUserIdFromContext();
        return profileService.getProfile(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<UserProfile> updateUserProfile(UserProfile userProfile) {
        userPreferencesValidator.validate(userProfile);
        UUID userId = getUserIdFromContext();
        return ResponseEntity.ok(profileService.upsertProfile(userId, userProfile));
    }

    @Override
    public ResponseEntity<UploadUrlResponse> getAvatarUploadUrl(String contentType) {
        UUID userId = getUserIdFromContext();
        return ResponseEntity.ok(profileService.getAvatarUploadUrl(userId, contentType));
    }

    @GetMapping("/internal/profiles/{userId}")
    public ResponseEntity<UserProfile> getInternalUserProfile(@PathVariable UUID userId) {
        return profileService.getProfile(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/internal/profiles/{userId}")
    public ResponseEntity<UserProfile> createInternalProfile(@PathVariable UUID userId, @RequestBody UserProfile userProfile) {
        return ResponseEntity.ok(profileService.upsertProfile(userId, userProfile));
    }

    @PostMapping("/internal/profiles/bulk")
    public ResponseEntity<Map<UUID, UserProfile>> getInternalUserProfiles(@RequestBody List<UUID> userIds) {
        return ResponseEntity.ok(profileService.getProfiles(userIds));
    }

    private UUID getUserIdFromContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
        }
        String userIdStr = (String) authentication.getCredentials();
        if (userIdStr == null || userIdStr.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User ID not found in security context");
        }
        return UUID.fromString(userIdStr);
    }
}
