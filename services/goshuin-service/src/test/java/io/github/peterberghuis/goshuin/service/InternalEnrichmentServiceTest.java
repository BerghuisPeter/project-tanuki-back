package io.github.peterberghuis.goshuin.service;

import io.github.peterberghuis.goshuin.entity.*;
import io.github.peterberghuis.goshuin.entity.EnrichmentStatus;
import io.github.peterberghuis.goshuin.internal.dto.*;
import io.github.peterberghuis.goshuin.mapper.InternalResourceMapper;
import io.github.peterberghuis.goshuin.repository.EnrichmentJobRepository;
import io.github.peterberghuis.goshuin.repository.GoshuinRepository;
import io.github.peterberghuis.goshuin.repository.TempleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalEnrichmentServiceTest {

    private final UUID templeId = UUID.randomUUID();
    private final UUID goshuinId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    @Mock
    private TempleRepository templeRepository;
    @Mock
    private GoshuinRepository goshuinRepository;
    @Mock
    private EnrichmentJobRepository enrichmentJobRepository;
    private InternalEnrichmentService service;

    @BeforeEach
    void setUp() {
        service = new InternalEnrichmentService(templeRepository, goshuinRepository, enrichmentJobRepository,
                new InternalResourceMapper());
    }

    // -------------------------------------------------------------------------
    // Reads
    // -------------------------------------------------------------------------

    @Test
    void getTemple_ReturnsTempleWithTranslationsAndEnrichment() {
        TempleEntity temple = temple();
        temple.setAffiliationType("buddhist");
        temple.getEnrichment().setStatus(EnrichmentStatus.PROCESSING);
        temple.getEnrichment().setAttempts(1);
        when(templeRepository.findById(templeId)).thenReturn(Optional.of(temple));

        InternalTemple result = service.getTemple(templeId);

        assertEquals(templeId, result.getId());
        assertEquals("ja", result.getOriginalLocale());
        assertNull(result.getLatitude());
        assertEquals("buddhist", result.getAffiliationType());
        assertEquals("PROCESSING", result.getEnrichment().getStatus().getValue());
        assertEquals(1, result.getEnrichment().getAttempts());
        assertEquals("浅草寺", result.getTranslations().get("ja").getName());
        assertEquals("東京", result.getTranslations().get("ja").getCity());
        assertNull(result.getTranslations().get("ja").getAddress());
    }

    @Test
    void getTemple_Unknown_Returns404() {
        when(templeRepository.findById(templeId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.getTemple(templeId));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void getGoshuin_EmbedsTempleContext() {
        TempleEntity temple = temple();
        temple.setLatitude(35.7148);
        temple.setLongitude(139.7967);
        GoshuinEntity goshuin = goshuin(temple);
        when(goshuinRepository.findById(goshuinId)).thenReturn(Optional.of(goshuin));

        InternalGoshuin result = service.getGoshuin(goshuinId);

        assertEquals(goshuinId, result.getId());
        assertEquals("written", result.getFormat());
        assertEquals("本尊", result.getTranslations().get("ja").getLabel());
        assertNull(result.getTranslations().get("ja").getDescription());
        assertEquals(templeId, result.getTemple().getId());
        assertEquals(35.7148, result.getTemple().getLatitude());
        assertEquals("浅草寺", result.getTemple().getTranslations().get("ja").getName());
    }

    @Test
    void getGoshuin_Unknown_Returns404() {
        when(goshuinRepository.findById(goshuinId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.getGoshuin(goshuinId));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // -------------------------------------------------------------------------
    // Temple: fill-only data
    // -------------------------------------------------------------------------

    @Test
    void updateTemple_FillsOnlyBlankFieldsAndKeepsExistingValues() {
        TempleEntity temple = temple();
        TempleI18nEntity ja = temple.getTranslations().iterator().next();
        ja.setDescription("human written");
        when(templeRepository.findById(templeId)).thenReturn(Optional.of(temple));

        TempleEnrichmentUpdate update = new TempleEnrichmentUpdate();
        update.setTranslations(Map.of("ja", translation("OVERWRITE", null, "東京都台東区浅草2-3-1", "generated")));

        service.updateTemple(templeId, update);

        assertEquals("浅草寺", ja.getName());
        assertEquals("東京", ja.getCity());
        assertEquals("東京都台東区浅草2-3-1", ja.getAddress());
        assertEquals("human written", ja.getDescription());
    }

    @Test
    void updateTemple_ReplayProducesSameState() {
        TempleEntity temple = temple();
        when(templeRepository.findById(templeId)).thenReturn(Optional.of(temple));

        TempleEnrichmentUpdate update = new TempleEnrichmentUpdate();
        update.setLatitude(35.7148);
        update.setLongitude(139.7967);
        update.setTranslations(Map.of("ja", translation(null, null, "first", null)));
        service.updateTemple(templeId, update);

        TempleEnrichmentUpdate replay = new TempleEnrichmentUpdate();
        replay.setLatitude(1.0);
        replay.setLongitude(2.0);
        replay.setTranslations(Map.of("ja", translation(null, null, "second", null)));
        service.updateTemple(templeId, replay);

        assertEquals(35.7148, temple.getLatitude());
        assertEquals(139.7967, temple.getLongitude());
        assertEquals("first", temple.getTranslations().iterator().next().getAddress());
    }

    @Test
    void updateTemple_CreatesMissingLocaleWhenNameGiven() {
        TempleEntity temple = temple();
        when(templeRepository.findById(templeId)).thenReturn(Optional.of(temple));

        TempleEnrichmentUpdate update = new TempleEnrichmentUpdate();
        update.setTranslations(Map.of("en", translation("Senso-ji", "Tokyo", "2-3-1 Asakusa", "desc")));

        service.updateTemple(templeId, update);

        TempleI18nEntity en = temple.getTranslations().stream()
                .filter(t -> t.getId().getLocale().equals("en")).findFirst().orElseThrow();
        assertEquals(templeId, en.getId().getTempleId());
        assertEquals("Senso-ji", en.getName());
        assertEquals("Tokyo", en.getCity());
        assertEquals("2-3-1 Asakusa", en.getAddress());
        assertEquals("desc", en.getDescription());
    }

    @Test
    void updateTemple_MissingLocaleWithoutName_IsRejected() {
        TempleEntity temple = temple();
        when(templeRepository.findById(templeId)).thenReturn(Optional.of(temple));

        TempleEnrichmentUpdate update = new TempleEnrichmentUpdate();
        update.setTranslations(Map.of("en", translation(null, "Tokyo", null, null)));

        assertThrows(IllegalArgumentException.class, () -> service.updateTemple(templeId, update));
    }

    @Test
    void updateTemple_CoordinatesMustBeSentTogether() {
        when(templeRepository.findById(templeId)).thenReturn(Optional.of(temple()));

        TempleEnrichmentUpdate update = new TempleEnrichmentUpdate();
        update.setLatitude(35.0);

        assertThrows(IllegalArgumentException.class, () -> service.updateTemple(templeId, update));
    }

    @Test
    void updateTemple_CoordinatesOutOfRange_AreRejected() {
        when(templeRepository.findById(templeId)).thenReturn(Optional.of(temple()));

        TempleEnrichmentUpdate update = new TempleEnrichmentUpdate();
        update.setLatitude(135.0);
        update.setLongitude(139.0);

        assertThrows(IllegalArgumentException.class, () -> service.updateTemple(templeId, update));
    }

    @Test
    void updateTemple_RejectsOverlongLocale() {
        when(templeRepository.findById(templeId)).thenReturn(Optional.of(temple()));

        TempleEnrichmentUpdate update = new TempleEnrichmentUpdate();
        update.setTranslations(Map.of("this-is-too-long", translation("x", null, null, null)));

        assertThrows(IllegalArgumentException.class, () -> service.updateTemple(templeId, update));
    }

    @Test
    void updateTemple_Unknown_Returns404() {
        when(templeRepository.findById(templeId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateTemple(templeId, new TempleEnrichmentUpdate()));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // -------------------------------------------------------------------------
    // Temple: lifecycle
    // -------------------------------------------------------------------------

    @Test
    void updateTemple_WithoutStatus_LeavesLifecycleUntouched() {
        TempleEntity temple = temple();
        when(templeRepository.findById(templeId)).thenReturn(Optional.of(temple));

        TempleEnrichmentUpdate update = new TempleEnrichmentUpdate();
        update.setJobId(jobId);
        update.setLatitude(35.0);
        update.setLongitude(139.0);

        service.updateTemple(templeId, update);

        assertEquals(EnrichmentStatus.PENDING, temple.getEnrichment().getStatus());
        assertEquals(0, temple.getEnrichment().getAttempts());
        assertNull(temple.getEnrichment().getLastAt());
        assertEquals(35.0, temple.getLatitude());
    }

    @Test
    void updateTemple_Processing_IncrementsAttemptsAndClearsErrorOnResourceAndJob() {
        TempleEntity temple = temple();
        temple.getEnrichment().setStatus(EnrichmentStatus.FAILED);
        temple.getEnrichment().setError("boom");
        temple.getEnrichment().setAttempts(2);
        EnrichmentJobEntity job = job(EnrichmentResourceType.TEMPLE, templeId);
        when(templeRepository.findById(templeId)).thenReturn(Optional.of(temple));
        when(enrichmentJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        TempleEnrichmentUpdate update = new TempleEnrichmentUpdate();
        update.setJobId(jobId);
        update.setStatus(EnrichmentUpdateStatus.PROCESSING);

        service.updateTemple(templeId, update);

        assertEquals(EnrichmentStatus.PROCESSING, temple.getEnrichment().getStatus());
        assertEquals(3, temple.getEnrichment().getAttempts());
        assertNull(temple.getEnrichment().getError());
        assertNotNull(temple.getEnrichment().getLastAt());
        assertEquals(EnrichmentStatus.PROCESSING, job.getStatus());
        assertEquals(3, job.getAttempts());
        assertNull(job.getError());
        assertNull(job.getCompletedAt());
    }

    @Test
    void updateTemple_Complete_WritesDataAndMarksJobCompleted() {
        TempleEntity temple = temple();
        temple.getEnrichment().setStatus(EnrichmentStatus.PROCESSING);
        temple.getEnrichment().setAttempts(1);
        EnrichmentJobEntity job = job(EnrichmentResourceType.TEMPLE, templeId);
        when(templeRepository.findById(templeId)).thenReturn(Optional.of(temple));
        when(enrichmentJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        TempleEnrichmentUpdate update = new TempleEnrichmentUpdate();
        update.setJobId(jobId);
        update.setStatus(EnrichmentUpdateStatus.COMPLETE);
        update.setLatitude(35.7148);
        update.setLongitude(139.7967);

        service.updateTemple(templeId, update);

        assertEquals(EnrichmentStatus.COMPLETE, temple.getEnrichment().getStatus());
        assertEquals(1, temple.getEnrichment().getAttempts());
        assertEquals(35.7148, temple.getLatitude());
        assertEquals(EnrichmentStatus.COMPLETE, job.getStatus());
        assertNotNull(job.getCompletedAt());
        assertEquals(temple.getEnrichment().getLastAt(), job.getCompletedAt());
    }

    @Test
    void updateTemple_Failed_StoresErrorAndDoesNotApplyData() {
        TempleEntity temple = temple();
        EnrichmentJobEntity job = job(EnrichmentResourceType.TEMPLE, templeId);
        when(templeRepository.findById(templeId)).thenReturn(Optional.of(temple));
        when(enrichmentJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        TempleEnrichmentUpdate update = new TempleEnrichmentUpdate();
        update.setJobId(jobId);
        update.setStatus(EnrichmentUpdateStatus.FAILED);
        update.setError("geocoding failed");
        update.setLatitude(35.0);
        update.setLongitude(139.0);
        update.setTranslations(Map.of("en", translation("Senso-ji", null, null, null)));

        service.updateTemple(templeId, update);

        assertEquals(EnrichmentStatus.FAILED, temple.getEnrichment().getStatus());
        assertEquals("geocoding failed", temple.getEnrichment().getError());
        assertEquals(EnrichmentStatus.FAILED, job.getStatus());
        assertEquals("geocoding failed", job.getError());
        assertNull(job.getCompletedAt());
        assertNull(temple.getLatitude());
        assertEquals(1, temple.getTranslations().size());
    }

    @Test
    void updateTemple_UnknownJob_Returns404() {
        when(templeRepository.findById(templeId)).thenReturn(Optional.of(temple()));
        when(enrichmentJobRepository.findById(jobId)).thenReturn(Optional.empty());

        TempleEnrichmentUpdate update = new TempleEnrichmentUpdate();
        update.setJobId(jobId);
        update.setStatus(EnrichmentUpdateStatus.PROCESSING);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.updateTemple(templeId, update));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void updateTemple_JobOfAnotherResource_IsRejected() {
        when(templeRepository.findById(templeId)).thenReturn(Optional.of(temple()));
        when(enrichmentJobRepository.findById(jobId))
                .thenReturn(Optional.of(job(EnrichmentResourceType.TEMPLE, UUID.randomUUID())));

        TempleEnrichmentUpdate update = new TempleEnrichmentUpdate();
        update.setJobId(jobId);
        update.setStatus(EnrichmentUpdateStatus.PROCESSING);

        assertThrows(IllegalArgumentException.class, () -> service.updateTemple(templeId, update));
    }

    // -------------------------------------------------------------------------
    // Goshuin
    // -------------------------------------------------------------------------

    @Test
    void updateGoshuin_FillsBlankAndCreatesMissingLocale() {
        GoshuinEntity goshuin = goshuin(temple());
        GoshuinI18nEntity ja = goshuin.getTranslations().iterator().next();
        when(goshuinRepository.findById(goshuinId)).thenReturn(Optional.of(goshuin));

        GoshuinEnrichmentUpdate update = new GoshuinEnrichmentUpdate();
        update.setTranslations(Map.of(
                "ja", goshuinTranslation("OVERWRITE", "ja description"),
                "en", goshuinTranslation("Main Hall", "en description")));

        service.updateGoshuin(goshuinId, update);

        assertEquals("本尊", ja.getLabel());
        assertEquals("ja description", ja.getDescription());
        GoshuinI18nEntity en = goshuin.getTranslations().stream()
                .filter(t -> t.getId().getLocale().equals("en")).findFirst().orElseThrow();
        assertEquals(goshuinId, en.getId().getGoshuinId());
        assertEquals("Main Hall", en.getLabel());
        assertEquals("en description", en.getDescription());
    }

    @Test
    void updateGoshuin_EmptyTranslationForMissingLocale_CreatesNothing() {
        GoshuinEntity goshuin = goshuin(temple());
        when(goshuinRepository.findById(goshuinId)).thenReturn(Optional.of(goshuin));

        GoshuinEnrichmentUpdate update = new GoshuinEnrichmentUpdate();
        update.setTranslations(Map.of("en", goshuinTranslation(" ", null)));

        service.updateGoshuin(goshuinId, update);

        assertEquals(1, goshuin.getTranslations().size());
    }

    @Test
    void updateGoshuin_Complete_UpdatesLifecycleOfGoshuinAndJob() {
        GoshuinEntity goshuin = goshuin(temple());
        goshuin.getEnrichment().setStatus(EnrichmentStatus.PROCESSING);
        EnrichmentJobEntity job = job(EnrichmentResourceType.GOSHUIN, goshuinId);
        when(goshuinRepository.findById(goshuinId)).thenReturn(Optional.of(goshuin));
        when(enrichmentJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        GoshuinEnrichmentUpdate update = new GoshuinEnrichmentUpdate();
        update.setJobId(jobId);
        update.setStatus(EnrichmentUpdateStatus.COMPLETE);

        service.updateGoshuin(goshuinId, update);

        assertEquals(EnrichmentStatus.COMPLETE, goshuin.getEnrichment().getStatus());
        assertEquals(EnrichmentStatus.COMPLETE, job.getStatus());
        assertNotNull(job.getCompletedAt());
    }

    @Test
    void updateGoshuin_Failed_DoesNotApplyTranslations() {
        GoshuinEntity goshuin = goshuin(temple());
        when(goshuinRepository.findById(goshuinId)).thenReturn(Optional.of(goshuin));

        GoshuinEnrichmentUpdate update = new GoshuinEnrichmentUpdate();
        update.setStatus(EnrichmentUpdateStatus.FAILED);
        update.setError("translation failed");
        update.setTranslations(Map.of("en", goshuinTranslation("Main Hall", null)));

        service.updateGoshuin(goshuinId, update);

        assertEquals(EnrichmentStatus.FAILED, goshuin.getEnrichment().getStatus());
        assertEquals("translation failed", goshuin.getEnrichment().getError());
        assertEquals(1, goshuin.getTranslations().size());
    }

    @Test
    void updateGoshuin_Unknown_Returns404() {
        when(goshuinRepository.findById(goshuinId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateGoshuin(goshuinId, new GoshuinEnrichmentUpdate()));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private TempleEntity temple() {
        TempleEntity temple = new TempleEntity();
        ReflectionTestUtils.setField(temple, "id", templeId);
        temple.setOriginalLocale("ja");
        temple.getTranslations().add(new TempleI18nEntity(temple, "ja", "浅草寺", null, null, null, "東京", null, null));
        return temple;
    }

    private GoshuinEntity goshuin(TempleEntity temple) {
        GoshuinEntity goshuin = new GoshuinEntity();
        ReflectionTestUtils.setField(goshuin, "id", goshuinId);
        goshuin.setFormat("written");
        goshuin.setOriginalLocale("ja");
        goshuin.setTemple(temple);
        goshuin.getTranslations().add(new GoshuinI18nEntity(goshuin, "ja", "本尊", null));
        return goshuin;
    }

    private EnrichmentJobEntity job(EnrichmentResourceType type, UUID resourceId) {
        EnrichmentJobEntity job = new EnrichmentJobEntity();
        ReflectionTestUtils.setField(job, "id", jobId);
        job.setResourceType(type);
        job.setResourceId(resourceId);
        job.setStatus(EnrichmentStatus.PENDING);
        return job;
    }

    private TempleTranslation translation(String name, String city, String address, String description) {
        TempleTranslation translation = new TempleTranslation();
        translation.setName(name);
        translation.setCity(city);
        translation.setAddress(address);
        translation.setDescription(description);
        return translation;
    }

    private GoshuinTranslation goshuinTranslation(String label, String description) {
        GoshuinTranslation translation = new GoshuinTranslation();
        translation.setLabel(label);
        translation.setDescription(description);
        return translation;
    }
}
