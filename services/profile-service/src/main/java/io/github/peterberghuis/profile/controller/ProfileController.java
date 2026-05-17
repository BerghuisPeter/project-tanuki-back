package io.github.peterberghuis.profile.controller;

import io.github.peterberghuis.profile.api.ProfileApi;
import io.github.peterberghuis.profile.dto.UploadUrlResponse;
import io.github.peterberghuis.profile.dto.UserProfile;
import io.github.peterberghuis.profile.service.UserProfileService;
import io.github.peterberghuis.profile.validator.UserProfileValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ProfileController implements ProfileApi {

    private final UserProfileService userProfileService;
    private final UserProfileValidator userPreferencesValidator;

    @Override
    public ResponseEntity<UserProfile> getUserProfile() {
        UUID userId = getUserIdFromContext();
        return userProfileService.getProfile(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<UserProfile> updateUserProfile(UserProfile userProfile) {
        userPreferencesValidator.validate(userProfile);
        UUID userId = getUserIdFromContext();
        return ResponseEntity.ok(userProfileService.upsertProfile(userId, userProfile));
    }

    @Override
    public ResponseEntity<UploadUrlResponse> getAvatarUploadUrl(String contentType) {
        UUID userId = getUserIdFromContext();
        return ResponseEntity.ok(userProfileService.getAvatarUploadUrl(userId, contentType));
    }

    private UUID getUserIdFromContext() {
        String userIdStr = (String) SecurityContextHolder.getContext().getAuthentication().getCredentials();
        if (userIdStr == null) {
            throw new RuntimeException("Unauthorized: No user ID in security context");
        }
        return UUID.fromString(userIdStr);
    }
}
