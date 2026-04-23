package io.github.peterberghuis.profile.service;

import io.github.peterberghuis.profile.dto.UserPreferences;
import io.github.peterberghuis.profile.entity.UserPreferencesEntity;
import io.github.peterberghuis.profile.repository.UserPreferencesRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserPreferencesServiceTest {

    @Mock
    private UserPreferencesRepository userPreferencesRepository;

    @InjectMocks
    private UserPreferencesService userPreferencesService;

    @Test
    void getPreferences_WhenExists_ShouldReturnDto() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UserPreferencesEntity entity = UserPreferencesEntity.builder()
                .userId(userId)
                .displayName("Test User")
                .color("#FF5733")
                .locale("en-US")
                .avatarUrl("https://example.com/avatar.png")
                .build();

        when(userPreferencesRepository.findById(userId)).thenReturn(Optional.of(entity));

        // Act
        Optional<UserPreferences> result = userPreferencesService.getPreferences(userId);

        // Assert
        assertTrue(result.isPresent());
        UserPreferences dto = result.get();
        assertEquals("Test User", dto.getDisplayName());
        assertEquals("#FF5733", dto.getColor());
        assertEquals("en-US", dto.getLocale());
        assertEquals("https://example.com/avatar.png", dto.getAvatarUrl());
    }

    @Test
    void getPreferences_WhenNotExists_ShouldReturnEmpty() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(userPreferencesRepository.findById(userId)).thenReturn(Optional.empty());

        // Act
        Optional<UserPreferences> result = userPreferencesService.getPreferences(userId);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void upsertPreferences_WhenNew_ShouldCreateAndReturnDto() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UserPreferences inputDto = new UserPreferences("en-US");
        inputDto.setDisplayName("New User");
        inputDto.setColor("#000000");
        inputDto.setAvatarUrl("https://example.com/new.png");

        when(userPreferencesRepository.findById(userId)).thenReturn(Optional.empty());
        when(userPreferencesRepository.save(any(UserPreferencesEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        UserPreferences result = userPreferencesService.upsertPreferences(userId, inputDto);

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
    void upsertPreferences_WhenExists_ShouldUpdateAndReturnDto() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UserPreferencesEntity existingEntity = UserPreferencesEntity.builder()
                .userId(userId)
                .displayName("Old Name")
                .color("#111111")
                .locale("en-GB")
                .build();

        UserPreferences updateDto = new UserPreferences("fr-FR");
        updateDto.setDisplayName("Updated Name");
        updateDto.setColor("#222222");
        updateDto.setAvatarUrl("https://example.com/updated.png");

        when(userPreferencesRepository.findById(userId)).thenReturn(Optional.of(existingEntity));
        when(userPreferencesRepository.save(any(UserPreferencesEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        UserPreferences result = userPreferencesService.upsertPreferences(userId, updateDto);

        // Assert
        assertNotNull(result);
        assertEquals("Updated Name", result.getDisplayName());
        assertEquals("#222222", result.getColor());
        assertEquals("fr-FR", result.getLocale());
        assertEquals("https://example.com/updated.png", result.getAvatarUrl());

        verify(userPreferencesRepository).save(argThat(entity ->
                entity.getUserId().equals(userId) &&
                        entity.getDisplayName().equals("Updated Name") &&
                        entity.getColor().equals("#222222") &&
                        entity.getLocale().equals("fr-FR")
        ));
    }

    @Test
    void upsertPreferences_PartialUpdate_ShouldOnlyUpdateProvidedFields() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UserPreferencesEntity existingEntity = UserPreferencesEntity.builder()
                .userId(userId)
                .displayName("Original Name")
                .color("#333333")
                .locale("en-US")
                .avatarUrl("https://example.com/original.png")
                .build();

        // DTO with only locale changed, other fields null
        UserPreferences updateDto = new UserPreferences("de-DE");
        updateDto.setDisplayName(null);
        updateDto.setColor(null);
        updateDto.setAvatarUrl(null);

        when(userPreferencesRepository.findById(userId)).thenReturn(Optional.of(existingEntity));
        when(userPreferencesRepository.save(any(UserPreferencesEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        UserPreferences result = userPreferencesService.upsertPreferences(userId, updateDto);

        // Assert
        assertEquals("Original Name", result.getDisplayName()); // Should remain unchanged
        assertEquals("#333333", result.getColor());           // Should remain unchanged
        assertEquals("de-DE", result.getLocale());           // Should be updated
        assertEquals("https://example.com/original.png", result.getAvatarUrl()); // Should remain unchanged

        verify(userPreferencesRepository).save(argThat(entity ->
                entity.getDisplayName().equals("Original Name") &&
                        entity.getLocale().equals("de-DE")
        ));
    }

    @Test
    void upsertPreferences_WithEmptyStrings_ShouldBeAllowed() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UserPreferencesEntity existingEntity = UserPreferencesEntity.builder()
                .userId(userId)
                .displayName("Some Name")
                .color("#123456")
                .locale("en-US")
                .avatarUrl("https://example.com/some.png")
                .build();

        UserPreferences updateDto = new UserPreferences("en-US");
        updateDto.setColor("");
        updateDto.setAvatarUrl("");

        when(userPreferencesRepository.findById(userId)).thenReturn(Optional.of(existingEntity));
        when(userPreferencesRepository.save(any(UserPreferencesEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        UserPreferences result = userPreferencesService.upsertPreferences(userId, updateDto);

        // Assert
        assertEquals("", result.getColor());
        assertEquals("", result.getAvatarUrl());

        verify(userPreferencesRepository).save(argThat(entity ->
                entity.getColor().equals("") &&
                        entity.getAvatarUrl().equals("")
        ));
    }
}
