package io.github.peterberghuis.goshuin.mapper;

import io.github.peterberghuis.goshuin.dto.*;
import io.github.peterberghuis.goshuin.entity.*;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class GoshuinMapper {

    public Goshuin mapToDto(GoshuinEntity entity, UserProfile userProfile) {
        Goshuin dto = new Goshuin();
        dto.setId(entity.getId());
        dto.setCreator(toCreatorDto(entity.getUserId(), userProfile));
        dto.setFormat(GoshuinFormat.fromValue(entity.getFormat()));
        dto.setTemple(toTempleLiteDto(entity.getTemple()));
        dto.setPages(entity.getPages());
        dto.setOriginalLocale(entity.getOriginalLocale());
        dto.setStartDate(entity.getStartDate());
        dto.setEndDate(entity.getEndDate());
        dto.setTranslations(toGoshuinTranslationMap(entity.getTranslations()));
        dto.setImages(toImageDtos(entity.getImages()));
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setEnrichment(toEnrichmentMetadataDto(entity.getEnrichment()));
        dto.setCommentCount(entity.getCommentCount());
        return dto;
    }

    private io.github.peterberghuis.goshuin.dto.EnrichmentMetadata toEnrichmentMetadataDto(io.github.peterberghuis.goshuin.entity.EnrichmentMetadata entity) {
        if (entity == null) return null;
        io.github.peterberghuis.goshuin.dto.EnrichmentMetadata dto = new io.github.peterberghuis.goshuin.dto.EnrichmentMetadata();
        dto.setStatus(io.github.peterberghuis.goshuin.dto.EnrichmentStatus.fromValue(entity.getStatus().name()));
        dto.setError(entity.getError());
        dto.setAttempts(entity.getAttempts());
        dto.setLastAt(entity.getLastAt());
        return dto;
    }

    private Creator toCreatorDto(UUID userId, UserProfile profile) {
        Creator creator = new Creator();
        creator.setUserId(userId);
        creator.setProfile(profile);
        return creator;
    }

    private List<GoshuinImage> toImageDtos(List<GoshuinImageEntity> imageEntities) {
        return imageEntities.stream()
                .map(imageEntity -> {
                    GoshuinImage dto = new GoshuinImage();
                    dto.setId(imageEntity.getId());
                    dto.setUrl(URI.create(imageEntity.getImageUrl()));
                    return dto;
                })
                .toList();
    }

    private Map<String, GoshuinTranslation> toGoshuinTranslationMap(Set<GoshuinI18nEntity> entities) {
        return entities.stream()
                .collect(Collectors.toMap(
                        t -> t.getId().getLocale(),
                        this::toGoshuinTranslationDto,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    private GoshuinTranslation toGoshuinTranslationDto(GoshuinI18nEntity entity) {
        GoshuinTranslation dto = new GoshuinTranslation();
        dto.setLabel(entity.getLabel());
        dto.setDescription(entity.getDescription());
        return dto;
    }

    private TempleLite toTempleLiteDto(TempleEntity entity) {
        if (entity == null) return null;

        TempleLite dto = new TempleLite();
        dto.setId(entity.getId());
        dto.setAffiliationType(AffiliationType.fromValue(entity.getAffiliationType()));
        dto.setLongitude(entity.getLongitude());
        dto.setLatitude(entity.getLatitude());
        dto.setOriginalLocale(entity.getOriginalLocale());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setEnrichment(toEnrichmentMetadataDto(entity.getEnrichment()));
        dto.setImageUrl(entity.getImageUrl() != null ? URI.create(entity.getImageUrl()) : null);
        dto.setTranslations(toTempleTranslationMap(entity.getTranslations()));
        return dto;
    }

    private Map<String, TempleTranslation> toTempleTranslationMap(Set<TempleI18nEntity> entities) {
        return entities.stream()
                .collect(Collectors.toMap(
                        t -> t.getId().getLocale(),
                        this::toTempleTranslationDto,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    private TempleTranslation toTempleTranslationDto(TempleI18nEntity entity) {
        TempleTranslation dto = new TempleTranslation();
        dto.setName(entity.getName());
        dto.setRegion(entity.getRegion());
        dto.setPostalCode(entity.getPostalCode());
        dto.setPrefecture(entity.getPrefecture());
        dto.setCity(entity.getCity());
        dto.setAddress(entity.getAddress());
        dto.setDescription(entity.getDescription());
        return dto;
    }
}
