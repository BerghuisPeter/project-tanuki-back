package io.github.peterberghuis.goshuin.service;

import io.github.peterberghuis.goshuin.client.ProfileClient;
import io.github.peterberghuis.goshuin.dto.*;
import io.github.peterberghuis.goshuin.entity.GoshuinEntity;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoshuinService {

    private final GoshuinRepository goshuinRepository;
    private final GoshuinRepositoryCustom goshuinRepositoryCustom;
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
            String cursorToken) {

//        lat = 35.634732;
//        lng = 139.615286;

        GoshuinCursor cursor = cursorToken != null && !cursorToken.isBlank()
                ? CursorUtils.decode(cursorToken)
                : null;

        Specification<GoshuinEntity> spec = GoshuinSpecifications.buildSpec(
                format, pages, affiliation, query, cursor);

        List<GoshuinEntity> entities = fetchSorted(spec, sort, limit, lat, lng, cursor);
        List<Goshuin> goshuins = toGoshuinDtos(entities);

        return paginatedResponse(goshuins, entities, limit, sort, lat, lng, cursor);
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