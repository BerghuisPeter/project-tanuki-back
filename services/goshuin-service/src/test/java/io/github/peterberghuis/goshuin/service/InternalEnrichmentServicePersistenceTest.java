package io.github.peterberghuis.goshuin.service;

import io.github.peterberghuis.goshuin.entity.*;
import io.github.peterberghuis.goshuin.entity.EnrichmentStatus;
import io.github.peterberghuis.goshuin.internal.dto.*;
import io.github.peterberghuis.goshuin.mapper.InternalResourceMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the internal enrichment writes against a real (H2) database, to check what the mock-based unit tests
 * can't: cascading of new i18n rows and persistence of the resource and job lifecycle.
 */
@DataJpaTest(properties = {
        "spring.test.database.replace=none",
        "spring.datasource.url=jdbc:h2:mem:internal-enrichment;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;INIT=CREATE SCHEMA IF NOT EXISTS goshuin_schema"
})
@ActiveProfiles("test")
@Import({InternalEnrichmentService.class, InternalResourceMapper.class})
class InternalEnrichmentServicePersistenceTest {

    @Autowired
    private InternalEnrichmentService service;

    @Autowired
    private EntityManager em;

    @Test
    void updateTemple_PersistsFilledDataNewLocaleAndLifecycle() {
        TempleEntity temple = new TempleEntity();
        temple.setOriginalLocale("ja");
        temple.getTranslations().add(new TempleI18nEntity(temple, "ja", "浅草寺", null, null, null, "東京", null, null));
        em.persist(temple);
        UUID templeId = temple.getId();
        EnrichmentJobEntity job = newJob(EnrichmentResourceType.TEMPLE, templeId);
        em.flush();
        em.clear();

        TempleEnrichmentUpdate update = new TempleEnrichmentUpdate();
        update.setJobId(job.getId());
        update.setStatus(EnrichmentUpdateStatus.COMPLETE);
        update.setLatitude(35.7148);
        update.setLongitude(139.7967);
        update.setTranslations(Map.of(
                "ja", templeTranslation("OVERWRITE", "東京都台東区浅草2-3-1"),
                "en", templeTranslation("Senso-ji", "2-3-1 Asakusa, Taito City, Tokyo")));
        service.updateTemple(templeId, update);
        em.flush();
        em.clear();

        TempleEntity stored = em.find(TempleEntity.class, templeId);
        assertEquals(35.7148, stored.getLatitude());
        assertEquals(139.7967, stored.getLongitude());
        assertEquals(EnrichmentStatus.COMPLETE, stored.getEnrichment().getStatus());
        assertNotNull(stored.getEnrichment().getLastAt());
        assertEquals(2, stored.getTranslations().size());
        TempleI18nEntity ja = stored.getTranslations().stream().filter(t -> t.getId().getLocale().equals("ja")).findFirst().orElseThrow();
        assertEquals("浅草寺", ja.getName());
        assertEquals("東京都台東区浅草2-3-1", ja.getAddress());
        TempleI18nEntity en = stored.getTranslations().stream().filter(t -> t.getId().getLocale().equals("en")).findFirst().orElseThrow();
        assertEquals("Senso-ji", en.getName());

        EnrichmentJobEntity storedJob = em.find(EnrichmentJobEntity.class, job.getId());
        assertEquals(EnrichmentStatus.COMPLETE, storedJob.getStatus());
        assertNotNull(storedJob.getCompletedAt());
    }

    @Test
    void updateGoshuin_PersistsNewLocaleAndGetEmbedsTemple() {
        TempleEntity temple = new TempleEntity();
        temple.setOriginalLocale("ja");
        temple.setLatitude(35.7148);
        temple.setLongitude(139.7967);
        temple.getTranslations().add(new TempleI18nEntity(temple, "ja", "浅草寺", null, null, null, "東京", null, null));
        em.persist(temple);

        GoshuinEntity goshuin = new GoshuinEntity();
        goshuin.setUserId(UUID.randomUUID());
        goshuin.setFormat("written");
        goshuin.setOriginalLocale("ja");
        goshuin.setTemple(temple);
        goshuin.getTranslations().add(new GoshuinI18nEntity(goshuin, "ja", "本尊", null));
        em.persist(goshuin);
        UUID goshuinId = goshuin.getId();
        em.flush();
        em.clear();

        GoshuinEnrichmentUpdate update = new GoshuinEnrichmentUpdate();
        update.setStatus(EnrichmentUpdateStatus.PROCESSING);
        update.setTranslations(Map.of("en", goshuinTranslation("Main Hall", "A seal from the main hall")));
        service.updateGoshuin(goshuinId, update);
        em.flush();
        em.clear();

        InternalGoshuin result = service.getGoshuin(goshuinId);

        assertEquals("PROCESSING", result.getEnrichment().getStatus().getValue());
        assertEquals(1, result.getEnrichment().getAttempts());
        assertEquals("Main Hall", result.getTranslations().get("en").getLabel());
        assertEquals("本尊", result.getTranslations().get("ja").getLabel());
        assertEquals(35.7148, result.getTemple().getLatitude());
        assertEquals("浅草寺", result.getTemple().getTranslations().get("ja").getName());
        assertNull(result.getEnrichment().getError());
    }

    private EnrichmentJobEntity newJob(EnrichmentResourceType type, UUID resourceId) {
        EnrichmentJobEntity job = new EnrichmentJobEntity();
        job.setResourceType(type);
        job.setResourceId(resourceId);
        job.setStatus(EnrichmentStatus.PENDING);
        em.persist(job);
        return job;
    }

    private TempleTranslation templeTranslation(String name, String address) {
        TempleTranslation translation = new TempleTranslation();
        translation.setName(name);
        translation.setAddress(address);
        return translation;
    }

    private GoshuinTranslation goshuinTranslation(String label, String description) {
        GoshuinTranslation translation = new GoshuinTranslation();
        translation.setLabel(label);
        translation.setDescription(description);
        return translation;
    }
}
