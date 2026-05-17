package io.github.peterberghuis.profile.controller;

import io.github.peterberghuis.profile.api.PreferencesApi;
import io.github.peterberghuis.profile.dto.UploadUrlResponse;
import io.github.peterberghuis.profile.dto.UserPreferences;
import io.github.peterberghuis.profile.service.UserPreferencesService;
import io.github.peterberghuis.profile.validator.UserPreferencesValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PreferencesController implements PreferencesApi {

    private final UserPreferencesService userPreferencesService;
    private final UserPreferencesValidator userPreferencesValidator;

    @Override
    public ResponseEntity<UserPreferences> getUserPreferences() {
        UUID userId = getUserIdFromContext();
        return userPreferencesService.getPreferences(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<UserPreferences> updateUserPreferences(UserPreferences userPreferences) {
        userPreferencesValidator.validate(userPreferences);
        UUID userId = getUserIdFromContext();
        return ResponseEntity.ok(userPreferencesService.upsertPreferences(userId, userPreferences));
    }

    @Override
    public ResponseEntity<UploadUrlResponse> getAvatarUploadUrl(String contentType) {
        UUID userId = getUserIdFromContext();
        return ResponseEntity.ok(userPreferencesService.getAvatarUploadUrl(userId, contentType));
    }

    private UUID getUserIdFromContext() {
        String userIdStr = (String) SecurityContextHolder.getContext().getAuthentication().getCredentials();
        if (userIdStr == null) {
            throw new RuntimeException("Unauthorized: No user ID in security context");
        }
        return UUID.fromString(userIdStr);
    }
}
