package io.github.peterberghuis.profile.service;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import io.github.peterberghuis.profile.config.GcpStorageProperties;
import io.github.peterberghuis.profile.dto.UploadUrlResponse;
import io.github.peterberghuis.profile.dto.UserProfile;
import io.github.peterberghuis.profile.entity.UserProfileEntity;
import io.github.peterberghuis.profile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileRepository userPreferencesRepository;
    private final Storage storage;
    private final GcpStorageProperties gcpStorageProperties;

    @Transactional(readOnly = true)
    public Optional<UserProfile> getProfile(UUID userId) {
        return userPreferencesRepository.findById(userId)
                .map(this::toDto);
    }

    @Transactional
    public UserProfile upsertProfile(UUID userId, UserProfile dto) {
        UserProfileEntity entity = userPreferencesRepository.findById(userId)
                .orElse(new UserProfileEntity());

        entity.setUserId(userId);
        if (dto.getDisplayName() != null) {
            entity.setDisplayName(dto.getDisplayName());
        }
        if (dto.getColor() != null) {
            entity.setColor(dto.getColor());
        }
        entity.setLocale(dto.getLocale());
        if (dto.getAvatarUrl() != null) {
            entity.setAvatarUrl(dto.getAvatarUrl());
        }

        UserProfileEntity saved = userPreferencesRepository.save(entity);
        return toDto(saved);
    }

    public UploadUrlResponse getAvatarUploadUrl(UUID userId, String contentType) {
        List<String> allowedContentTypes = gcpStorageProperties.getAvatar().getAllowedContentTypes();
        if (contentType == null || !allowedContentTypes.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid content type. Allowed: " + allowedContentTypes);
        }

        String fileName = "avatars/" + userId + "-" + UUID.randomUUID();
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(gcpStorageProperties.getBucketName(), fileName))
                .setContentType(contentType)
                .build();

        URL url = storage.signUrl(blobInfo, 15, TimeUnit.MINUTES,
                Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                Storage.SignUrlOption.withExtHeaders(java.util.Collections.singletonMap("Content-Type", contentType)),
                Storage.SignUrlOption.withV4Signature());

        UploadUrlResponse response = new UploadUrlResponse();
        response.setUploadUrl(url.toString());
        response.setFileName(fileName);
        response.setMaxSizeBytes(gcpStorageProperties.getAvatar().getMaxSizeBytes());
        response.setAllowedContentTypes(allowedContentTypes);
        return response;
    }

    private UserProfile toDto(UserProfileEntity entity) {
        UserProfile dto = new UserProfile();
        dto.setDisplayName(entity.getDisplayName());
        dto.setColor(entity.getColor());
        dto.setLocale(entity.getLocale());
        dto.setAvatarUrl(entity.getAvatarUrl());
        return dto;
    }
}
