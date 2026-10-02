package io.github.peterberghuis.goshuin.service;

import io.github.peterberghuis.goshuin.entity.*;
import io.github.peterberghuis.goshuin.entity.EnrichmentMetadata;
import io.github.peterberghuis.goshuin.entity.EnrichmentStatus;
import io.github.peterberghuis.goshuin.internal.dto.*;
import io.github.peterberghuis.goshuin.mapper.InternalResourceMapper;
import io.github.peterberghuis.goshuin.repository.EnrichmentJobRepository;
import io.github.peterberghuis.goshuin.repository.GoshuinRepository;
import io.github.peterberghuis.goshuin.repository.TempleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Backs the internal API used by the enrichment worker (ENRICHMENT.md, section 5).
 * <p>
 * Writes are fill-only: a stored value is only written where it is currently null or blank, so replaying a
 * request is harmless and human-entered data is never overwritten.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InternalEnrichmentService {

    private static final int MAX_LOCALE_LENGTH = 10;

    private final TempleRepository templeRepository;
    private final GoshuinRepository goshuinRepository;
    private final EnrichmentJobRepository enrichmentJobRepository;
    private final InternalResourceMapper mapper;

    private static String fill(String current, String incoming) {
        return isBlank(current) && !isBlank(incoming) ? incoming : current;
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Transactional(readOnly = true)
    public InternalTemple getTemple(UUID id) {
        return mapper.toDto(findTemple(id));
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public InternalGoshuin getGoshuin(UUID id) {
        return mapper.toDto(findGoshuin(id));
    }

    @Transactional
    public void updateTemple(UUID id, TempleEnrichmentUpdate update) {
        TempleEntity temple = findTemple(id);
        validateCoordinates(update.getLatitude(), update.getLongitude());

        applyLifecycle(temple.getEnrichment(), update.getStatus(), update.getError(),
                findJob(update.getJobId(), update.getStatus(), EnrichmentResourceType.TEMPLE, id));

        if (update.getStatus() != EnrichmentUpdateStatus.FAILED) {
            fillCoordinates(temple, update.getLatitude(), update.getLongitude());
            if (update.getTranslations() != null) {
                update.getTranslations().forEach((locale, translation) -> fillTranslation(temple, locale, translation));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Fill-only writes
    // -------------------------------------------------------------------------

    @Transactional
    public void updateGoshuin(UUID id, GoshuinEnrichmentUpdate update) {
        GoshuinEntity goshuin = findGoshuin(id);

        applyLifecycle(goshuin.getEnrichment(), update.getStatus(), update.getError(),
                findJob(update.getJobId(), update.getStatus(), EnrichmentResourceType.GOSHUIN, id));

        if (update.getStatus() != EnrichmentUpdateStatus.FAILED && update.getTranslations() != null) {
            update.getTranslations().forEach((locale, translation) -> fillTranslation(goshuin, locale, translation));
        }
    }

    /**
     * Applies a reported status to the resource's {@code enrichment} metadata and to its job row, so the two
     * always agree. Does nothing when no status was reported.
     */
    private void applyLifecycle(EnrichmentMetadata metadata, EnrichmentUpdateStatus status, String error, EnrichmentJobEntity job) {
        if (status == null) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();

        switch (status) {
            case PROCESSING -> {
                metadata.setStatus(EnrichmentStatus.PROCESSING);
                metadata.setAttempts(metadata.getAttempts() + 1);
                metadata.setError(null);
            }
            case COMPLETE -> {
                metadata.setStatus(EnrichmentStatus.COMPLETE);
                metadata.setError(null);
            }
            case FAILED -> {
                metadata.setStatus(EnrichmentStatus.FAILED);
                metadata.setError(error);
            }
        }
        metadata.setLastAt(now);

        if (job != null) {
            job.setStatus(metadata.getStatus());
            job.setError(metadata.getError());
            job.setAttempts(metadata.getAttempts());
            job.setCompletedAt(status == EnrichmentUpdateStatus.COMPLETE ? now : null);
        }
    }

    private EnrichmentJobEntity findJob(UUID jobId, EnrichmentUpdateStatus status, EnrichmentResourceType resourceType, UUID resourceId) {
        if (jobId == null || status == null) {
            return null;
        }
        EnrichmentJobEntity job = enrichmentJobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enrichment job not found: " + jobId));
        if (job.getResourceType() != resourceType || !job.getResourceId().equals(resourceId)) {
            throw new IllegalArgumentException("Enrichment job " + jobId + " does not belong to " + resourceType + " " + resourceId);
        }
        return job;
    }

    private void validateCoordinates(Double latitude, Double longitude) {
        if ((latitude == null) != (longitude == null)) {
            throw new IllegalArgumentException("latitude and longitude must be sent together");
        }
        if (latitude != null && (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180)) {
            throw new IllegalArgumentException("latitude or longitude out of range");
        }
    }

    private void fillCoordinates(TempleEntity temple, Double latitude, Double longitude) {
        if (latitude != null && temple.getLatitude() == null && temple.getLongitude() == null) {
            temple.setLatitude(latitude);
            temple.setLongitude(longitude);
        }
    }

    private void fillTranslation(TempleEntity temple, String locale, TempleTranslation incoming) {
        requireValidLocale(locale);
        if (incoming == null) {
            return;
        }
        TempleI18nEntity existing = temple.getTranslations().stream()
                .filter(t -> t.getId().getLocale().equals(locale))
                .findFirst()
                .orElse(null);

        if (existing == null) {
            if (isBlank(incoming.getName())) {
                throw new IllegalArgumentException("name is required to add the '" + locale + "' translation of temple " + temple.getId());
            }
            temple.getTranslations().add(new TempleI18nEntity(temple, locale, incoming.getName(), blankToNull(incoming.getRegion()),
                    blankToNull(incoming.getPostalCode()), blankToNull(incoming.getPrefecture()), blankToNull(incoming.getCity()),
                    blankToNull(incoming.getAddress()), blankToNull(incoming.getDescription())));
            return;
        }
        existing.setName(fill(existing.getName(), incoming.getName()));
        existing.setRegion(fill(existing.getRegion(), incoming.getRegion()));
        existing.setPostalCode(fill(existing.getPostalCode(), incoming.getPostalCode()));
        existing.setPrefecture(fill(existing.getPrefecture(), incoming.getPrefecture()));
        existing.setCity(fill(existing.getCity(), incoming.getCity()));
        existing.setAddress(fill(existing.getAddress(), incoming.getAddress()));
        existing.setDescription(fill(existing.getDescription(), incoming.getDescription()));
    }

    private void fillTranslation(GoshuinEntity goshuin, String locale, GoshuinTranslation incoming) {
        requireValidLocale(locale);
        if (incoming == null) {
            return;
        }
        GoshuinI18nEntity existing = goshuin.getTranslations().stream()
                .filter(t -> t.getId().getLocale().equals(locale))
                .findFirst()
                .orElse(null);

        if (existing == null) {
            if (!isBlank(incoming.getLabel()) || !isBlank(incoming.getDescription())) {
                goshuin.getTranslations().add(new GoshuinI18nEntity(goshuin, locale,
                        blankToNull(incoming.getLabel()), blankToNull(incoming.getDescription())));
            }
            return;
        }
        existing.setLabel(fill(existing.getLabel(), incoming.getLabel()));
        existing.setDescription(fill(existing.getDescription(), incoming.getDescription()));
    }

    private void requireValidLocale(String locale) {
        if (isBlank(locale) || locale.length() > MAX_LOCALE_LENGTH) {
            throw new IllegalArgumentException("Invalid locale: '" + locale + "'");
        }
    }

    // -------------------------------------------------------------------------
    // Lookups
    // -------------------------------------------------------------------------

    private TempleEntity findTemple(UUID id) {
        return templeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Temple not found: " + id));
    }

    private GoshuinEntity findGoshuin(UUID id) {
        return goshuinRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Goshuin not found: " + id));
    }
}
