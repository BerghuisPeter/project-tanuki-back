package io.github.peterberghuis.goshuin.service;

import io.github.peterberghuis.goshuin.client.ProfileClient;
import io.github.peterberghuis.goshuin.dto.*;
import io.github.peterberghuis.goshuin.entity.*;
import io.github.peterberghuis.goshuin.model.CommentCountCursor;
import io.github.peterberghuis.goshuin.model.CreatedAtCursor;
import io.github.peterberghuis.goshuin.model.GoshuinCursor;
import io.github.peterberghuis.goshuin.model.ProximityCursor;
import io.github.peterberghuis.goshuin.repository.GoshuinRepository;
import io.github.peterberghuis.goshuin.repository.GoshuinRepositoryCustom;
import io.github.peterberghuis.goshuin.repository.GoshuinSpecifications;
import io.github.peterberghuis.goshuin.util.CursorUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GoshuinService {

    private final GoshuinRepository goshuinRepository;
    private final GoshuinRepositoryCustom goshuinRepositoryCustom;
    private final ProfileClient profileClient;

    public GoshuinSearchResponse searchGoshuins(
            GoshuinFormat format,
            List<Integer> pages,
            AffiliationType affiliation,
            String query,
            GoshuinSort sort,
            Integer limit,
            String cursorToken) {

        double lat = 35.634732;
        double lon = 139.615286;

        GoshuinCursor cursor = cursorToken != null && !cursorToken.isBlank()
                ? CursorUtils.decode(cursorToken)
                : null;

        Specification<GoshuinEntity> spec = GoshuinSpecifications.buildSpec(
                format, pages, affiliation, query, cursor);

        List<GoshuinEntity> entities = fetchSorted(spec, sort, limit, lat, lon, cursor);
        List<Goshuin> goshuins = toGoshuinDtos(entities);

        return paginatedResponse(goshuins, entities, limit, sort, lat, lon, cursor);
    }

    // -------------------------------------------------------------------------
    // Search helpers
    // -------------------------------------------------------------------------

    private List<GoshuinEntity> fetchSorted(
            Specification<GoshuinEntity> spec, GoshuinSort sort, int limit, double lat, double lon, GoshuinCursor cursor) {

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
                .map(e -> mapToDto(e, profilesByUserId.get(e.getUserId())))
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
            List<Goshuin> goshuins, List<GoshuinEntity> entities, int limit, GoshuinSort sort, double lat, double lon, GoshuinCursor cursor) {

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

    // -------------------------------------------------------------------------
    // DTO mappers
    // -------------------------------------------------------------------------

    private Goshuin mapToDto(GoshuinEntity entity, UserProfile userProfile) {
        Goshuin dto = new Goshuin();
        dto.setId(entity.getId());
        dto.setCreator(toCreatorDto(entity.getUserId(), userProfile));
        dto.setFormat(GoshuinFormat.fromValue(entity.getFormat()));
        dto.setTemple(toTempleLiteDto(entity.getTemple()));
        dto.setPages(entity.getPages());
        dto.setStartDate(entity.getStartDate());
        dto.setEndDate(entity.getEndDate());
        dto.setTranslations(toGoshuinTranslationMap(entity.getTranslations()));
        dto.setImages(toImageDtos(entity.getImages()));
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setCommentCount(entity.getCommentCount());
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
        dto.setLongitude(entity.getLongitude().doubleValue());
        dto.setLatitude(entity.getLatitude().doubleValue());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
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