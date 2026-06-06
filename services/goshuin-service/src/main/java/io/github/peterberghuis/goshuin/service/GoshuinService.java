package io.github.peterberghuis.goshuin.service;

import io.github.peterberghuis.goshuin.client.ProfileClient;
import io.github.peterberghuis.goshuin.dto.*;
import io.github.peterberghuis.goshuin.entity.*;
import io.github.peterberghuis.goshuin.repository.GoshuinRepository;
import io.github.peterberghuis.goshuin.repository.GoshuinSpecifications;
import io.github.peterberghuis.goshuin.repository.TempleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GoshuinService {

    private final GoshuinRepository goshuinRepository;
    private final TempleRepository templeRepository;
    private final ProfileClient profileClient;

    public List<Goshuin> searchGoshuins(GoshuinFormat format, List<Integer> pages, LocalDate startDate, LocalDate endDate, AffiliationType affiliation, String query, GoshuinSort sort) {
        Specification<GoshuinEntity> spec = Specification
                .where(GoshuinSpecifications.withFormat(format))
                .and(GoshuinSpecifications.withPages(pages))
                .and(GoshuinSpecifications.withStartDate(startDate))
                .and(GoshuinSpecifications.withEndDate(endDate))
                .and(GoshuinSpecifications.withAffiliation(affiliation))
                .and(
                        GoshuinSpecifications.withLabel(query).or(GoshuinSpecifications.withTempleTranslationSearch(query))
                );

        List<GoshuinEntity> entities = switch (sort) {
            case CREATED_AT -> goshuinRepository.findAll(spec, GoshuinSpecifications.CREATED_AT_SORT);

            case COMMENT_COUNT -> goshuinRepository.findAll(spec, GoshuinSpecifications.COMMENT_COUNT_SORT);

            case NEARBY -> goshuinRepository.findAll(spec, GoshuinSpecifications.CREATED_AT_SORT);
        };

        List<UUID> userIds = entities.stream()
                .map(GoshuinEntity::getUserId)
                .distinct()
                .toList();

        Map<UUID, UserProfile> userProfilesResponse =
                profileClient.getInternalProfiles(userIds);

        Map<UUID, UserProfile> userProfiles =
                userProfilesResponse != null ? userProfilesResponse : Map.of();

        return entities.stream()
                .map(entity -> mapToDto(
                        entity,
                        userProfiles.get(entity.getUserId())
                ))
                .toList();
    }

    public Goshuin createGoshuin(GoshuinCreate goshuinCreate) {
        GoshuinEntity entity = new GoshuinEntity();
        entity.setUserId(goshuinCreate.getUserId());
        entity.setFormat(goshuinCreate.getFormat().toString());
        entity.setPages(goshuinCreate.getPages());
        entity.setStartDate(goshuinCreate.getStartDate());
        entity.setEndDate(goshuinCreate.getEndDate());

        TempleEntity temple = templeRepository.findById(goshuinCreate.getTempleId())
                .orElseThrow(() -> new RuntimeException("Temple not found"));
        entity.setTemple(temple);

        if (goshuinCreate.getImages() != null) {
            for (GoshuinImage imageDto : goshuinCreate.getImages()) {
                if (imageDto.getUrl() != null) {
                    GoshuinImageEntity imageEntity = new GoshuinImageEntity(entity, imageDto.getUrl().toString());
                    entity.getImages().add(imageEntity);
                }
            }
        }

        if (goshuinCreate.getTranslations() != null) {
            for (Map.Entry<String, GoshuinTranslation> entry : goshuinCreate.getTranslations().entrySet()) {
                GoshuinI18nEntity translationEntity = new GoshuinI18nEntity(
                        entity,
                        entry.getKey(),
                        entry.getValue().getLabel(),
                        entry.getValue().getDescription()
                );
                entity.getTranslations().add(translationEntity);
            }
        }

        GoshuinEntity savedEntity = goshuinRepository.save(entity);
        UserProfile profile = profileClient.getInternalProfile(savedEntity.getUserId());
        return mapToDto(savedEntity, profile);
    }

    private Goshuin mapToDto(GoshuinEntity entity, UserProfile userProfile) {
        Goshuin dto = new Goshuin();
        dto.setId(entity.getId());

        Creator creator = new Creator();
        creator.setUserId(entity.getUserId());
        creator.setProfile(userProfile);
        dto.setCreator(creator);

        dto.setFormat(GoshuinFormat.fromValue(entity.getFormat()));
        dto.setTemple(mapToSummaryDto(entity.getTemple()));
        dto.setPages(entity.getPages());
        dto.setStartDate(entity.getStartDate());
        dto.setEndDate(entity.getEndDate());

        dto.setTranslations(
                mapGoshuinTranslations(entity.getTranslations())
        );
        dto.setImages(entity.getImages().stream()
                .map(imageEntity -> {
                    GoshuinImage imageDto = new GoshuinImage();
                    imageDto.setId(imageEntity.getId());
                    imageDto.setUrl(URI.create(imageEntity.getImageUrl()));
                    return imageDto;
                })
                .toList());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setCommentCount(entity.getCommentCount());

        return dto;
    }

    private Map<String, GoshuinTranslation> mapGoshuinTranslations(
            Set<GoshuinI18nEntity> entities
    ) {
        return entities.stream()
                .collect(Collectors.toMap(
                        t -> t.getId().getLocale(),
                        this::mapGoshuinTranslation,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    private GoshuinTranslation mapGoshuinTranslation(GoshuinI18nEntity entity) {
        GoshuinTranslation dto = new GoshuinTranslation();
        dto.setLabel(entity.getLabel());
        dto.setDescription(entity.getDescription());
        return dto;
    }

    private TempleLite mapToSummaryDto(TempleEntity entity) {
        if (entity == null) return null;
        TempleLite dto = new TempleLite();
        dto.setId(entity.getId());
        dto.setAffiliationType(AffiliationType.fromValue(entity.getAffiliationType()));
        dto.setLongitude(entity.getLongitude().doubleValue());
        dto.setLatitude(entity.getLatitude().doubleValue());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        Map<String, TempleTranslation> translations =
                entity.getTranslations()
                        .stream()
                        .collect(Collectors.toMap(
                                t -> t.getId().getLocale(),
                                this::mapTempleTranslation,
                                (a, b) -> a,
                                LinkedHashMap::new
                        ));
        dto.setTranslations(translations);

        return dto;
    }

    private TempleTranslation mapTempleTranslation(TempleI18nEntity entity) {
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
