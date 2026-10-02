package io.github.peterberghuis.goshuin.mapper;

import io.github.peterberghuis.goshuin.entity.GoshuinEntity;
import io.github.peterberghuis.goshuin.entity.GoshuinI18nEntity;
import io.github.peterberghuis.goshuin.entity.TempleEntity;
import io.github.peterberghuis.goshuin.entity.TempleI18nEntity;
import io.github.peterberghuis.goshuin.internal.dto.*;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps entities to the DTOs of the internal enrichment API. Must be called inside a transaction,
 * because the translations are lazy.
 */
@Component
public class InternalResourceMapper {

    public InternalTemple toDto(TempleEntity temple) {
        InternalTemple dto = new InternalTemple();
        dto.setId(temple.getId());
        dto.setOriginalLocale(temple.getOriginalLocale());
        dto.setLatitude(temple.getLatitude());
        dto.setLongitude(temple.getLongitude());
        dto.setAffiliationType(temple.getAffiliationType());
        dto.setEnrichment(toDto(temple.getEnrichment()));
        dto.setTranslations(toTempleTranslations(temple));
        return dto;
    }

    public InternalGoshuin toDto(GoshuinEntity goshuin) {
        InternalGoshuin dto = new InternalGoshuin();
        dto.setId(goshuin.getId());
        dto.setOriginalLocale(goshuin.getOriginalLocale());
        dto.setFormat(goshuin.getFormat());
        dto.setEnrichment(toDto(goshuin.getEnrichment()));

        Map<String, GoshuinTranslation> translations = new LinkedHashMap<>();
        for (GoshuinI18nEntity entity : goshuin.getTranslations()) {
            GoshuinTranslation translation = new GoshuinTranslation();
            translation.setLabel(entity.getLabel());
            translation.setDescription(entity.getDescription());
            translations.put(entity.getId().getLocale(), translation);
        }
        dto.setTranslations(translations);
        dto.setTemple(toContext(goshuin.getTemple()));
        return dto;
    }

    private InternalTempleContext toContext(TempleEntity temple) {
        InternalTempleContext dto = new InternalTempleContext();
        dto.setId(temple.getId());
        dto.setLatitude(temple.getLatitude());
        dto.setLongitude(temple.getLongitude());
        dto.setAffiliationType(temple.getAffiliationType());
        dto.setTranslations(toTempleTranslations(temple));
        return dto;
    }

    private Map<String, TempleTranslation> toTempleTranslations(TempleEntity temple) {
        Map<String, TempleTranslation> translations = new LinkedHashMap<>();
        for (TempleI18nEntity entity : temple.getTranslations()) {
            TempleTranslation translation = new TempleTranslation();
            translation.setName(entity.getName());
            translation.setRegion(entity.getRegion());
            translation.setPostalCode(entity.getPostalCode());
            translation.setPrefecture(entity.getPrefecture());
            translation.setCity(entity.getCity());
            translation.setAddress(entity.getAddress());
            translation.setDescription(entity.getDescription());
            translations.put(entity.getId().getLocale(), translation);
        }
        return translations;
    }

    private EnrichmentMetadata toDto(io.github.peterberghuis.goshuin.entity.EnrichmentMetadata metadata) {
        EnrichmentMetadata dto = new EnrichmentMetadata();
        dto.setStatus(EnrichmentStatus.fromValue(metadata.getStatus().name()));
        dto.setError(metadata.getError());
        dto.setAttempts(metadata.getAttempts());
        dto.setLastAt(metadata.getLastAt());
        return dto;
    }
}
