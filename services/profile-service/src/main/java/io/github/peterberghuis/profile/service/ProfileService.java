package io.github.peterberghuis.profile.service;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import io.github.peterberghuis.common.dto.UploadUrlResponse;
import io.github.peterberghuis.gcp.config.GcpStorageProperties;
import io.github.peterberghuis.profile.config.AvatarStorageProperties;
import io.github.peterberghuis.profile.dto.UserProfile;
import io.github.peterberghuis.profile.entity.UserProfileEntity;
import io.github.peterberghuis.profile.event.AvatarChangedEvent;
import io.github.peterberghuis.profile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProfileService {
    private final UserProfileRepository userProfileRepository;
    private final Storage storage;
    private final GcpStorageProperties gcpStorageProperties;
    private final AvatarStorageProperties avatarStorageProperties;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Optional<UserProfile> getProfile(UUID userId) {
        return userProfileRepository.findById(userId)
                .map(this::toDto);
    }

    @Transactional
    public UserProfile upsertProfile(UUID userId, UserProfile dto) {
        UserProfileEntity entity = userProfileRepository.findById(userId)
                .orElse(new UserProfileEntity());

        if (dto.getAvatarUrl() != null) {
            String oldAvatarUrl = entity.getAvatarUrl();
            String newAvatarUrl = dto.getAvatarUrl();

            if (shouldDeleteOldAvatar(oldAvatarUrl, newAvatarUrl)) {
                String blobName = extractBlobName(oldAvatarUrl);
                if (blobName != null) {
                    eventPublisher.publishEvent(new AvatarChangedEvent(gcpStorageProperties.getBucketName(), blobName));
                }
            }
            entity.setAvatarUrl(newAvatarUrl);
        }

        entity.setUserId(userId);
        if (dto.getDisplayName() != null) {
            entity.setDisplayName(dto.getDisplayName());
        }
        if (dto.getColor() != null) {
            entity.setColor(dto.getColor());
        }
        if (dto.getLocale() != null) {
            entity.setLocale(dto.getLocale());
        }

        UserProfileEntity saved = userProfileRepository.save(entity);
        return toDto(saved);
    }

    private boolean shouldDeleteOldAvatar(String oldAvatarUrl, String newAvatarUrl) {
        if (oldAvatarUrl == null || oldAvatarUrl.isEmpty()) {
            return false;
        }
        // If new URL is empty, we definitely delete old one
        if (newAvatarUrl.isEmpty()) {
            return true;
        }
        // If they are different, we delete the old one
        return !oldAvatarUrl.equals(newAvatarUrl);
    }

    private String extractBlobName(String avatarUrl) {
        // Expected format: avatars/userId-uuid
        // The avatarUrl could be a full GCP URL or just the path if we decided so.
        // Looking at getAvatarUploadUrl, fileName is "avatars/" + userId + "-" + UUID.randomUUID()
        // If the avatarUrl stored is the fileName or contains it.
        if (avatarUrl.contains("avatars/")) {
            String blobName = avatarUrl.substring(avatarUrl.indexOf("avatars/"));
            // Strip query parameters if present (e.g. signed URLs)
            if (blobName.contains("?")) {
                blobName = blobName.substring(0, blobName.indexOf("?"));
            }
            return blobName;
        }
        return null;
    }

    public UploadUrlResponse getAvatarUploadUrl(UUID userId, String contentType) {
        List<String> allowedContentTypes = avatarStorageProperties.getAllowedContentTypes();
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
        response.setMaxSizeBytes(avatarStorageProperties.getMaxSizeBytes());
        response.setAllowedContentTypes(allowedContentTypes);
        return response;
    }

    @Transactional(readOnly = true)
    public Map<UUID, UserProfile> getProfiles(List<UUID> userIds) {
        return userProfileRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserProfileEntity::getUserId, this::toDto));
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
