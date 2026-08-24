package io.github.peterberghuis.goshuin.service;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import io.github.peterberghuis.common.dto.UploadUrlResponse;
import io.github.peterberghuis.gcp.config.GcpStorageProperties;
import io.github.peterberghuis.goshuin.client.ProfileClient;
import io.github.peterberghuis.goshuin.config.GoshuinImageStorageProperties;
import io.github.peterberghuis.goshuin.dto.*;
import io.github.peterberghuis.goshuin.dto.EnrichmentStatus;
import io.github.peterberghuis.goshuin.entity.*;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class GoshuinService {

    private final GoshuinRepository goshuinRepository;
    private final GoshuinRepositoryCustom goshuinRepositoryCustom;
    private final TempleService templeService;
    private final EnrichmentService enrichmentService;
    private final ProfileClient profileClient;
    private final GoshuinMapper goshuinMapper;
    private final Storage storage;
    private final GcpStorageProperties gcpStorageProperties;
    private final GoshuinImageStorageProperties goshuinImageStorageProperties;

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
            UUID userId,
            List<EnrichmentStatus> enrichmentStatuses) {

//        lat = 35.634732;
//        lng = 139.615286;

        GoshuinCursor cursor = cursorToken != null && !cursorToken.isBlank()
                ? CursorUtils.decode(cursorToken)
                : null;

        Specification<GoshuinEntity> spec = GoshuinSpecifications.buildSpec(
                format, pages, affiliation, query, cursor, userId, enrichmentStatuses);

        List<GoshuinEntity> entities = fetchSorted(spec, sort, limit, lat, lng, cursor);
        List<Goshuin> goshuins = toGoshuinDtos(entities);

        return paginatedResponse(goshuins, entities, limit, sort, lat, lng, cursor);
    }

    public UploadUrlResponse getGoshuinUploadUrl(UUID userId, String contentType) {
        List<String> allowedContentTypes = goshuinImageStorageProperties.getAllowedContentTypes();
        if (contentType == null || !allowedContentTypes.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid content type. Allowed: " + allowedContentTypes);
        }

        String fileName = "goshuins/" + userId + "-" + UUID.randomUUID();
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(gcpStorageProperties.getBucketName(), fileName))
                .setContentType(contentType)
                .build();

        URL url = storage.signUrl(blobInfo, 15, TimeUnit.MINUTES,
                Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                Storage.SignUrlOption.withExtHeaders(Collections.singletonMap("Content-Type", contentType)),
                Storage.SignUrlOption.withV4Signature());

        return UploadUrlResponse.builder()
                .uploadUrl(url.toString())
                .fileName(fileName)
                .maxSizeBytes(goshuinImageStorageProperties.getMaxSizeBytes())
                .allowedContentTypes(allowedContentTypes)
                .build();
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
        if (goshuinCreate.getImageUrls() == null || goshuinCreate.getImageUrls().isEmpty()) {
            throw new IllegalArgumentException("At least one image URL must be provided");
        }

        TempleEntity templeEntity = templeService.resolveTemple(
                goshuinCreate.getTempleId(),
                goshuinCreate.getTemple(),
                goshuinCreate.getOriginalLocale()
        );

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
        enrichmentService.createJob(EnrichmentResourceType.GOSHUIN, saved.getId());

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