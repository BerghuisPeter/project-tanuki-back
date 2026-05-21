package io.github.peterberghuis.profile.service;

import io.github.peterberghuis.profile.config.GcpStorageProperties;
import io.github.peterberghuis.profile.dto.UploadUrlResponse;
import io.github.peterberghuis.profile.dto.UserProfile;
import io.github.peterberghuis.profile.entity.UserProfileEntity;
import io.github.peterberghuis.profile.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserProfileRepository userPreferencesRepository;

    @InjectMocks
    private UserProfileService userProfileService;

    @Mock
    private com.google.cloud.storage.Storage storage;

    private GcpStorageProperties gcpStorageProperties;

    @BeforeEach
    void setUp() {
        gcpStorageProperties = new GcpStorageProperties();
        gcpStorageProperties.setBucketName("test-bucket");
        gcpStorageProperties.getAvatar().setAllowedContentTypes(List.of("image/jpeg", "image/png"));
        gcpStorageProperties.getAvatar().setMaxSizeBytes(5242880L);

        ReflectionTestUtils.setField(userProfileService, "gcpStorageProperties", gcpStorageProperties);
    }

    @Test
    void getProfile_WhenExists_ShouldReturnDto() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UserProfileEntity entity = UserProfileEntity.builder()
                .userId(userId)
                .displayName("Test User")
                .color("#FF5733")
                .locale("en-US")
                .avatarUrl("https://example.com/avatar.png")
                .build();

        when(userPreferencesRepository.findById(userId)).thenReturn(Optional.of(entity));

        // Act
        Optional<UserProfile> result = userProfileService.getProfile(userId);

        // Assert
        assertTrue(result.isPresent());
        UserProfile dto = result.get();
        assertEquals("Test User", dto.getDisplayName());
        assertEquals("#FF5733", dto.getColor());
        assertEquals("en-US", dto.getLocale());
        assertEquals("https://example.com/avatar.png", dto.getAvatarUrl());
    }

    @Test
    void getProfile_WhenNotExists_ShouldReturnEmpty() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(userPreferencesRepository.findById(userId)).thenReturn(Optional.empty());

        // Act
        Optional<UserProfile> result = userProfileService.getProfile(userId);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void upsertProfile_WhenNew_ShouldCreateAndReturnDto() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UserProfile inputDto = new UserProfile();
        inputDto.setDisplayName("New User");
        inputDto.setColor("#000000");
        inputDto.setLocale("en-US");
        inputDto.setAvatarUrl("https://example.com/new.png");

        when(userPreferencesRepository.findById(userId)).thenReturn(Optional.empty());
        when(userPreferencesRepository.save(any(UserProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        UserProfile result = userProfileService.upsertProfile(userId, inputDto);

        // Assert
        assertNotNull(result);
        assertEquals("New User", result.getDisplayName());
        assertEquals("#000000", result.getColor());
        assertEquals("en-US", result.getLocale());
        assertEquals("https://example.com/new.png", result.getAvatarUrl());

        verify(userPreferencesRepository).save(argThat(entity ->
                entity.getUserId().equals(userId) &&
                        entity.getDisplayName().equals("New User") &&
                        entity.getColor().equals("#000000") &&
                        entity.getLocale().equals("en-US") &&
                        entity.getAvatarUrl().equals("https://example.com/new.png")
        ));
    }

    @Test
    void upsertProfile_WhenExists_ShouldUpdateAndReturnDto() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UserProfileEntity existingEntity = UserProfileEntity.builder()
                .userId(userId)
                .displayName("Old Name")
                .color("#111111")
                .build();

        UserProfile updateDto = new UserProfile();
        updateDto.setDisplayName("Updated Name");
        updateDto.setColor("#222222");
        updateDto.setLocale("fr-FR");
        updateDto.setAvatarUrl("https://example.com/updated.png");

        when(userPreferencesRepository.findById(userId)).thenReturn(Optional.of(existingEntity));
        when(userPreferencesRepository.save(any(UserProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        UserProfile result = userProfileService.upsertProfile(userId, updateDto);

        // Assert
        assertNotNull(result);
        assertEquals("Updated Name", result.getDisplayName());
        assertEquals("#222222", result.getColor());
        assertEquals("fr-FR", result.getLocale());
        assertEquals("https://example.com/updated.png", result.getAvatarUrl());

        verify(userPreferencesRepository).save(argThat(entity ->
                entity.getUserId().equals(userId) &&
                        entity.getDisplayName().equals("Updated Name") &&
                        entity.getLocale().equals("fr-FR") &&
                        entity.getColor().equals("#222222")
        ));
    }

    @Test
    void upsertProfile_PartialUpdate_ShouldOnlyUpdateProvidedFields() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UserProfileEntity existingEntity = UserProfileEntity.builder()
                .userId(userId)
                .displayName("Original Name")
                .color("#333333")
                .avatarUrl("https://example.com/original.png")
                .build();

        // DTO with no fields changed, all null
        UserProfile updateDto = new UserProfile();
        updateDto.setDisplayName(null);
        updateDto.setColor(null);
        updateDto.setAvatarUrl(null);

        when(userPreferencesRepository.findById(userId)).thenReturn(Optional.of(existingEntity));
        when(userPreferencesRepository.save(any(UserProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        UserProfile result = userProfileService.upsertProfile(userId, updateDto);

        // Assert
        assertEquals("Original Name", result.getDisplayName()); // Should remain unchanged
        assertEquals("#333333", result.getColor());           // Should remain unchanged
        assertEquals("https://example.com/original.png", result.getAvatarUrl()); // Should remain unchanged

        verify(userPreferencesRepository).save(argThat(entity ->
                entity.getDisplayName().equals("Original Name")
        ));
    }

    @Test
    void upsertProfile_WithEmptyStrings_ShouldBeAllowed() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UserProfileEntity existingEntity = UserProfileEntity.builder()
                .userId(userId)
                .displayName("Some Name")
                .color("#123456")
                .avatarUrl("https://example.com/some.png")
                .build();

        UserProfile updateDto = new UserProfile();
        updateDto.setColor("");
        updateDto.setAvatarUrl("");

        when(userPreferencesRepository.findById(userId)).thenReturn(Optional.of(existingEntity));
        when(userPreferencesRepository.save(any(UserProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        UserProfile result = userProfileService.upsertProfile(userId, updateDto);

        // Assert
        assertEquals("", result.getColor());
        assertEquals("", result.getAvatarUrl());

        verify(userPreferencesRepository).save(argThat(entity ->
                entity.getColor().equals("") &&
                        entity.getAvatarUrl().equals("")
        ));
    }

    @Test
    void getAvatarUploadUrl_WithValidContentType_ShouldReturnUrl() throws java.net.MalformedURLException {
        // Arrange
        UUID userId = UUID.randomUUID();
        String contentType = "image/jpeg";
        java.net.URL mockUrl = new java.net.URL("https://storage.googleapis.com/test-bucket/avatars/test");
        when(storage.signUrl(any(), anyLong(), any(), any(), any(), any())).thenReturn(mockUrl);

        // Act
        UploadUrlResponse result = userProfileService.getAvatarUploadUrl(userId, contentType);

        // Assert
        assertNotNull(result);
        assertEquals(mockUrl.toString(), result.getUploadUrl());
        assertTrue(result.getFileName().startsWith("avatars/" + userId));
        assertEquals(5242880L, result.getMaxSizeBytes());
        assertEquals(List.of("image/jpeg", "image/png"), result.getAllowedContentTypes());
    }

    @Test
    void getAvatarUploadUrl_WithInvalidContentType_ShouldThrowException() {
        // Arrange
        UUID userId = UUID.randomUUID();
        String contentType = "application/pdf";

        // Act & Assert
        assertThrows(ResponseStatusException.class, () ->
                userProfileService.getAvatarUploadUrl(userId, contentType)
        );
    }
}
