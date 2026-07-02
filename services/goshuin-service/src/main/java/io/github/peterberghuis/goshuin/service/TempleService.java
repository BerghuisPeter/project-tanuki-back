package io.github.peterberghuis.goshuin.service;

import io.github.peterberghuis.goshuin.dto.Temple;
import io.github.peterberghuis.goshuin.dto.TempleCreate;
import io.github.peterberghuis.goshuin.dto.TempleTranslation;
import io.github.peterberghuis.goshuin.entity.TempleEntity;
import io.github.peterberghuis.goshuin.entity.TempleI18nEntity;
import io.github.peterberghuis.goshuin.repository.TempleRepository;
import io.github.peterberghuis.goshuin.repository.TempleSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TempleService {

    private final TempleRepository templeRepository;

    public List<Temple> searchTemples(String query) {
        Specification<TempleEntity> spec = TempleSpecifications.search(query);
        return templeRepository.findAll(spec).stream()
                .map(this::mapToDto)
                .toList();
    }

    public TempleEntity getTempleEntityById(UUID id) {
        return templeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Temple not found with id: " + id));
    }

    @Transactional
    public Temple createTemple(TempleCreate templeCreate) {
        TempleEntity saved = createTempleInternal(templeCreate);
        return mapToDto(saved);
    }

    @Transactional
    public TempleEntity createTempleInternal(TempleCreate templeCreate) {
        TempleEntity entity = new TempleEntity();
        entity.setLongitude(templeCreate.getLongitude());
        entity.setLatitude(templeCreate.getLatitude());
        entity.setAffiliationType(templeCreate.getAffiliationType().toString());
        entity.setWebsiteUrl(templeCreate.getWebsiteUrl() != null ? templeCreate.getWebsiteUrl().toString() : null);
        entity.setPhoneNumber(templeCreate.getPhoneNumber());
        if (templeCreate.getGoshuinServiceOpenUntil() != null) {
            entity.setGoshuinServiceOpenUntil(LocalTime.parse(templeCreate.getGoshuinServiceOpenUntil()));
        }
        entity.setImageUrl(templeCreate.getImageUrl() != null ? templeCreate.getImageUrl().toString() : null);
        if (templeCreate.getOriginalLocale() != null) {
            entity.setOriginalLocale(templeCreate.getOriginalLocale());
        }

        if (templeCreate.getTranslations() != null) {
            templeCreate.getTranslations().forEach((locale, translationDto) -> {
                TempleI18nEntity translation = new TempleI18nEntity(
                        entity,
                        locale,
                        translationDto.getName(),
                        translationDto.getRegion(),
                        translationDto.getPostalCode(),
                        translationDto.getPrefecture(),
                        translationDto.getCity(),
                        translationDto.getAddress(),
                        translationDto.getDescription()
                );
                entity.getTranslations().add(translation);
            });
        }

        return templeRepository.save(entity);
    }

    private Temple mapToDto(TempleEntity entity) {
        Temple dto = new Temple();
        dto.setId(entity.getId());
        dto.setLongitude(entity.getLongitude().doubleValue());
        dto.setLatitude(entity.getLatitude().doubleValue());
        dto.setAffiliationType(io.github.peterberghuis.goshuin.dto.AffiliationType.fromValue(entity.getAffiliationType()));
        dto.setWebsiteUrl(entity.getWebsiteUrl() != null ? java.net.URI.create(entity.getWebsiteUrl()) : null);
        dto.setPhoneNumber(entity.getPhoneNumber());
        if (entity.getGoshuinServiceOpenUntil() != null) {
            dto.setGoshuinServiceOpenUntil(entity.getGoshuinServiceOpenUntil().toString());
        }
        dto.setImageUrl(entity.getImageUrl() != null ? java.net.URI.create(entity.getImageUrl()) : null);
        dto.setOriginalLocale(entity.getOriginalLocale());

        Map<String, TempleTranslation> translations = new HashMap<>();
        for (TempleI18nEntity translationEntity : entity.getTranslations()) {
            TempleTranslation translationDto = new TempleTranslation();
            translationDto.setName(translationEntity.getName());
            translationDto.setRegion(translationEntity.getRegion());
            translationDto.setPostalCode(translationEntity.getPostalCode());
            translationDto.setPrefecture(translationEntity.getPrefecture());
            translationDto.setCity(translationEntity.getCity());
            translationDto.setAddress(translationEntity.getAddress());
            translationDto.setDescription(translationEntity.getDescription());
            translations.put(translationEntity.getId().getLocale(), translationDto);
        }
        dto.setTranslations(translations);
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        return dto;
    }
}
