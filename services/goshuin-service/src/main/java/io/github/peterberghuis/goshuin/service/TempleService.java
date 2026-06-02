package io.github.peterberghuis.goshuin.service;

import io.github.peterberghuis.goshuin.dto.Temple;
import io.github.peterberghuis.goshuin.dto.TempleCreate;
import io.github.peterberghuis.goshuin.dto.TempleTranslation;
import io.github.peterberghuis.goshuin.entity.TempleEntity;
import io.github.peterberghuis.goshuin.entity.TempleI18nEntity;
import io.github.peterberghuis.goshuin.repository.TempleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TempleService {

    private final TempleRepository templeRepository;

    public List<Temple> getAllTemples() {
        return templeRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public Temple createTemple(TempleCreate templeCreate) {
        TempleEntity entity = new TempleEntity();
        entity.setLongitude(BigDecimal.valueOf(templeCreate.getLongitude()));
        entity.setLatitude(BigDecimal.valueOf(templeCreate.getLatitude()));
        entity.setAffiliationType(templeCreate.getAffiliationType().toString());
        entity.setWebsiteUrl(templeCreate.getWebsiteUrl() != null ? templeCreate.getWebsiteUrl().toString() : null);
        entity.setPhoneNumber(templeCreate.getPhoneNumber());
        entity.setGoshuinType(templeCreate.getGoshuinType());
        if (templeCreate.getGoshuinServiceOpenUntil() != null) {
            entity.setGoshuinServiceOpenUntil(LocalTime.parse(templeCreate.getGoshuinServiceOpenUntil()));
        }
        entity.setImageUrl(templeCreate.getImageUrl() != null ? templeCreate.getImageUrl().toString() : null);

        if (templeCreate.getTranslations() != null) {
            templeCreate.getTranslations().forEach((locale, translationDto) -> {
                TempleI18nEntity translation = new TempleI18nEntity(
                        entity,
                        locale,
                        translationDto.getName(),
                        translationDto.getAddress(),
                        translationDto.getDescription()
                );
                entity.getTranslations().add(translation);
            });
        }

        TempleEntity saved = templeRepository.save(entity);
        return mapToDto(saved);
    }

    private Temple mapToDto(TempleEntity entity) {
        Temple dto = new Temple();
        dto.setId(entity.getId());
        dto.setLongitude(entity.getLongitude().doubleValue());
        dto.setLatitude(entity.getLatitude().doubleValue());
        dto.setAffiliationType(io.github.peterberghuis.goshuin.dto.AffiliationType.fromValue(entity.getAffiliationType()));
        dto.setWebsiteUrl(entity.getWebsiteUrl() != null ? java.net.URI.create(entity.getWebsiteUrl()) : null);
        dto.setPhoneNumber(entity.getPhoneNumber());
        dto.setGoshuinType(entity.getGoshuinType());
        if (entity.getGoshuinServiceOpenUntil() != null) {
            dto.setGoshuinServiceOpenUntil(entity.getGoshuinServiceOpenUntil().toString());
        }
        dto.setImageUrl(entity.getImageUrl() != null ? java.net.URI.create(entity.getImageUrl()) : null);

        Map<String, TempleTranslation> translations = new HashMap<>();
        for (TempleI18nEntity translationEntity : entity.getTranslations()) {
            TempleTranslation translationDto = new TempleTranslation();
            translationDto.setName(translationEntity.getName());
            translationDto.setAddress(translationEntity.getAddress());
            translationDto.setDescription(translationEntity.getDescription());
            translations.put(translationEntity.getId().getLocale(), translationDto);
        }
        dto.setTranslations(translations);

        return dto;
    }
}
