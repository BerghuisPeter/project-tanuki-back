package io.github.peterberghuis.profile.controller;

import io.github.peterberghuis.profile.api.ProfileApi;
import io.github.peterberghuis.profile.dto.UploadUrlResponse;
import io.github.peterberghuis.profile.dto.UserProfile;
import io.github.peterberghuis.profile.service.ProfileService;
import io.github.peterberghuis.profile.validator.UserProfileValidator;
import io.github.peterberghuis.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
        UUID userId = SecurityUtils.getUserIdFromContext();
        return profileService.getProfile(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<UserProfile> updateUserProfile(UserProfile userProfile) {
        userPreferencesValidator.validate(userProfile);
        UUID userId = SecurityUtils.getUserIdFromContext();
        return ResponseEntity.ok(profileService.upsertProfile(userId, userProfile));
    }

    @Override
    public ResponseEntity<UploadUrlResponse> getAvatarUploadUrl(String contentType) {
        UUID userId = SecurityUtils.getUserIdFromContext();
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
}
