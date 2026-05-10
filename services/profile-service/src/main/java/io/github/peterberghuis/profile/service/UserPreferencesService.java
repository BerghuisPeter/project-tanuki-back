package io.github.peterberghuis.profile.service;

import io.github.peterberghuis.profile.dto.UserPreferences;
import io.github.peterberghuis.profile.entity.UserPreferencesEntity;
import io.github.peterberghuis.profile.repository.UserPreferencesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserPreferencesService {

    private final UserPreferencesRepository userPreferencesRepository;

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

    private UserPreferences toDto(UserPreferencesEntity entity) {
        UserPreferences dto = new UserPreferences();
        dto.setDisplayName(entity.getDisplayName());
        dto.setColor(entity.getColor());
        dto.setLocale(entity.getLocale());
        dto.setAvatarUrl(entity.getAvatarUrl());
        return dto;
    }
}
