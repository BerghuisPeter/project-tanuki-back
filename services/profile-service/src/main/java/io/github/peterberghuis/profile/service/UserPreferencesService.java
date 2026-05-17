package io.github.peterberghuis.profile.service;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import io.github.peterberghuis.profile.dto.UploadUrlResponse;
import io.github.peterberghuis.profile.dto.UserPreferences;
import io.github.peterberghuis.profile.entity.UserPreferencesEntity;
import io.github.peterberghuis.profile.repository.UserPreferencesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
public class UserPreferencesService {

    private final UserPreferencesRepository userPreferencesRepository;
    private final Storage storage;

    @Value("${gcp.storage.bucket-name}")
    private String bucketName;

    @Value("${gcp.storage.avatar.allowed-content-types}")
    private List<String> allowedContentTypes;

    @Value("${gcp.storage.avatar.max-size-bytes}")
    private long maxSizeBytes;

    @Transactional(readOnly = true)
    public Optional<UserPreferences> getPreferences(UUID userId) {
        return userPreferencesRepository.findById(userId)
                .map(this::toDto);
    }

    @Transactional
    public UserPreferences upsertPreferences(UUID userId, UserPreferences dto) {
        UserPreferencesEntity entity = userPreferencesRepository.findById(userId)
                .orElse(new UserPreferencesEntity());

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

        UserPreferencesEntity saved = userPreferencesRepository.save(entity);
        return toDto(saved);
    }

    public UploadUrlResponse getAvatarUploadUrl(UUID userId, String contentType) {
        if (contentType == null || !allowedContentTypes.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid content type. Allowed: " + allowedContentTypes);
        }

        String fileName = "avatars/" + userId + "-" + UUID.randomUUID();
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, fileName))
                .setContentType(contentType)
                .build();

        URL url = storage.signUrl(blobInfo, 15, TimeUnit.MINUTES,
                Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                Storage.SignUrlOption.withExtHeaders(java.util.Collections.singletonMap("Content-Type", contentType)),
                Storage.SignUrlOption.withV4Signature());

        UploadUrlResponse response = new UploadUrlResponse();
        response.setUploadUrl(url.toString());
        response.setFileName(fileName);
        response.setMaxSizeBytes(maxSizeBytes);
        response.setAllowedContentTypes(allowedContentTypes);
        return response;
    }

    private UserPreferences toDto(UserPreferencesEntity entity) {
        UserPreferences dto = new UserPreferences();
        dto.setDisplayName(entity.getDisplayName());
        dto.setColor(entity.getColor());
        dto.setLocale(entity.getLocale());
        dto.setAvatarUrl(entity.getAvatarUrl());
        return dto;
    }
}
