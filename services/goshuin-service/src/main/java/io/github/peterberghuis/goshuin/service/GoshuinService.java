package io.github.peterberghuis.goshuin.service;

import io.github.peterberghuis.goshuin.client.ProfileClient;
import io.github.peterberghuis.goshuin.dto.*;
import io.github.peterberghuis.goshuin.entity.GoshuinEntity;
import io.github.peterberghuis.goshuin.entity.GoshuinI18nEntity;
import io.github.peterberghuis.goshuin.entity.GoshuinImageEntity;
import io.github.peterberghuis.goshuin.entity.TempleEntity;
import io.github.peterberghuis.goshuin.mapper.GoshuinMapper;
import io.github.peterberghuis.goshuin.model.CommentCountCursor;
import io.github.peterberghuis.goshuin.model.CreatedAtCursor;
import io.github.peterberghuis.goshuin.model.GoshuinCursor;
import io.github.peterberghuis.goshuin.model.ProximityCursor;
import io.github.peterberghuis.goshuin.repository.GoshuinRepository;
import io.github.peterberghuis.goshuin.repository.GoshuinRepositoryCustom;
import io.github.peterberghuis.goshuin.repository.GoshuinSpecifications;
import io.github.peterberghuis.goshuin.util.CursorUtils;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoshuinService {

    private final GoshuinRepository goshuinRepository;
    private final GoshuinRepositoryCustom goshuinRepositoryCustom;
    private final TempleService templeService;
    private final ProfileClient profileClient;
    private final GoshuinMapper goshuinMapper;

    public GoshuinSearchResponse searchGoshuins(
            GoshuinFormat format,
            List<Integer> pages,
            AffiliationType affiliation,
            String query,
            GoshuinSort sort,
            Integer limit,
            Double lat,
            Double lng,
            String cursorToken,
            UUID userId) {

//        lat = 35.634732;
//        lng = 139.615286;

        GoshuinCursor cursor = cursorToken != null && !cursorToken.isBlank()
                ? CursorUtils.decode(cursorToken)
                : null;

        Specification<GoshuinEntity> spec = GoshuinSpecifications.buildSpec(
                format, pages, affiliation, query, cursor, userId);

        List<GoshuinEntity> entities = fetchSorted(spec, sort, limit, lat, lng, cursor);
        List<Goshuin> goshuins = toGoshuinDtos(entities);

        return paginatedResponse(goshuins, entities, limit, sort, lat, lng, cursor);
    }

    private static @NonNull GoshuinEntity buildGoshuinEntity(UUID userId, GoshuinCreate goshuinCreate, TempleEntity templeEntity) {
        GoshuinEntity entity = new GoshuinEntity();
        entity.setUserId(userId);
        entity.setTemple(templeEntity);
        entity.setFormat(goshuinCreate.getFormat().getValue());
        if (goshuinCreate.getPages() != null) {
            entity.setPages(goshuinCreate.getPages());
        }
        if (goshuinCreate.getOriginalLocale() != null) {
            entity.setOriginalLocale(goshuinCreate.getOriginalLocale());
        }
        entity.setStartDate(goshuinCreate.getStartDate());
        entity.setEndDate(goshuinCreate.getEndDate());
        return entity;
    }

    @Transactional
    public Goshuin createGoshuin(UUID userId, GoshuinCreate goshuinCreate) {
        if (goshuinCreate.getTempleId() != null && goshuinCreate.getTemple() != null) {
            throw new IllegalArgumentException("Only one of templeId or temple definition can be provided");
        }

        if (goshuinCreate.getImageUrls() == null || goshuinCreate.getImageUrls().isEmpty()) {
            throw new IllegalArgumentException("At least one image URL must be provided");
        }

        TempleEntity templeEntity;
        if (goshuinCreate.getTempleId() != null) {
            templeEntity = templeService.getTempleEntityById(goshuinCreate.getTempleId());
        } else if (goshuinCreate.getTemple() != null) {
            // ToDo
            //  replace with enrichment service for the temple.
            //  For now attach it to the default temple.
            String uuidString = "11111111-1111-1111-1111-111111111111";
            templeEntity = templeService.getTempleEntityById(UUID.fromString(uuidString));
        } else {
            throw new IllegalArgumentException("Either templeId or temple definition must be provided");
        }

        GoshuinEntity entity = buildGoshuinEntity(userId, goshuinCreate, templeEntity);

        if (goshuinCreate.getTranslations() != null) {
            goshuinCreate.getTranslations().forEach((locale, translationDto) -> {
                GoshuinI18nEntity translation = new GoshuinI18nEntity(
                        entity,
                        locale,
                        translationDto.getLabel(),
                        translationDto.getDescription()
                );
                entity.getTranslations().add(translation);
            });
        }

        if (goshuinCreate.getImageUrls() != null) {
            for (URI url : goshuinCreate.getImageUrls()) {
                GoshuinImageEntity image = new GoshuinImageEntity(entity, url.toString());
                entity.getImages().add(image);
            }
        }

        GoshuinEntity saved = goshuinRepository.saveAndFlush(entity);

        Map<UUID, UserProfile> profiles = profileClient.getInternalProfiles(List.of(userId));
        UserProfile userProfile = profiles != null ? profiles.get(userId) : null;
        return goshuinMapper.mapToDto(saved, userProfile);
    }

    // -------------------------------------------------------------------------
    // Search helpers
    // -------------------------------------------------------------------------

    private List<GoshuinEntity> fetchSorted(
            Specification<GoshuinEntity> spec, GoshuinSort sort, int limit, Double lat, Double lon, GoshuinCursor cursor) {

        return switch (sort) {
            case CREATED_AT -> goshuinRepository
                    .findAll(spec, PageRequest.of(0, limit + 1, GoshuinSpecifications.CREATED_AT_SORT))
                    .getContent();

            case COMMENT_COUNT -> goshuinRepository.findAll(spec, GoshuinSpecifications.COMMENT_COUNT_SORT);

            case PROXIMITY ->
                    goshuinRepositoryCustom.findAllByDistance(spec, lat, lon, limit, (ProximityCursor) cursor);
        };
    }

    private List<Goshuin> toGoshuinDtos(List<GoshuinEntity> entities) {
        Map<UUID, UserProfile> profilesByUserId = fetchProfiles(entities);
        return entities.stream()
                .map(e -> goshuinMapper.mapToDto(e, profilesByUserId.get(e.getUserId())))
                .toList();
    }

    private Map<UUID, UserProfile> fetchProfiles(List<GoshuinEntity> entities) {
        List<UUID> userIds = entities.stream()
                .map(GoshuinEntity::getUserId)
                .distinct()
                .toList();
        Map<UUID, UserProfile> profiles = profileClient.getInternalProfiles(userIds);
        return profiles != null ? profiles : Map.of();
    }

    private GoshuinSearchResponse paginatedResponse(
            List<Goshuin> goshuins, List<GoshuinEntity> entities, int limit, GoshuinSort sort, Double lat, Double lon, GoshuinCursor cursor) {

        String nextPageToken = null;

        if (entities.size() > limit) {
            GoshuinEntity last = entities.get(limit - 1);
            nextPageToken = switch (sort) {
                case CREATED_AT -> CursorUtils.encode(new CreatedAtCursor(last.getCreatedAt(), last.getId()));
                case COMMENT_COUNT -> CursorUtils.encode(new CommentCountCursor(last.getCommentCount(), last.getId()));
                case PROXIMITY -> {
                    int currentOffset = cursor instanceof ProximityCursor(int offset) ? offset : 0;
                    yield CursorUtils.encode(new ProximityCursor(currentOffset + limit));
                }
            };
            goshuins = goshuins.subList(0, limit);
        }

        GoshuinSearchResponse response = new GoshuinSearchResponse();
        response.setGoshuins(goshuins);
        response.setNextPageToken(nextPageToken);
        return response;
    }

}