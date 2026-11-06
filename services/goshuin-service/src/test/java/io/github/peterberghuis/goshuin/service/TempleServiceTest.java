package io.github.peterberghuis.goshuin.service;

import io.github.peterberghuis.goshuin.dto.AffiliationType;
import io.github.peterberghuis.goshuin.dto.GoshuinCreateTemple;
import io.github.peterberghuis.goshuin.dto.Temple;
import io.github.peterberghuis.goshuin.dto.TempleCreate;
import io.github.peterberghuis.goshuin.dto.TempleTranslation;
import io.github.peterberghuis.goshuin.entity.EnrichmentResourceType;
import io.github.peterberghuis.goshuin.entity.TempleEntity;
import io.github.peterberghuis.goshuin.repository.TempleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TempleServiceTest {

    @Mock
    private TempleRepository templeRepository;

    @Mock
    private EnrichmentService enrichmentService;

    @InjectMocks
    private TempleService templeService;

    @Test
    void createTempleInternal_ShouldCreateEnrichmentJob() {
        // Arrange
        TempleCreate templeCreate = new TempleCreate();
        templeCreate.setAffiliationType(AffiliationType.SHINTO);

        TempleEntity templeEntity = new TempleEntity();
        UUID templeId = UUID.randomUUID();
        ReflectionTestUtils.setField(templeEntity, "id", templeId);

        when(templeRepository.save(any())).thenReturn(templeEntity);

        // Act
        templeService.createTempleInternal(templeCreate);

        // Assert
        verify(templeRepository).save(any());
        verify(enrichmentService).createJob(EnrichmentResourceType.TEMPLE, templeId);
    }

    @Test
    void createTemple_WithoutAffiliationTypeOrCoordinates_ShouldSucceedWithNullFields() {
        // Arrange: only the required translations are given, enrichment fills the rest in later
        TempleTranslation translation = new TempleTranslation();
        translation.setName("浅草寺");
        TempleCreate templeCreate = new TempleCreate();
        templeCreate.setTranslations(Map.of("ja", translation));

        when(templeRepository.save(any())).thenAnswer(invocation -> {
            TempleEntity saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });

        // Act
        Temple result = templeService.createTemple(templeCreate);

        // Assert
        ArgumentCaptor<TempleEntity> captor = ArgumentCaptor.forClass(TempleEntity.class);
        verify(templeRepository).save(captor.capture());
        assertNull(captor.getValue().getAffiliationType());
        assertNull(captor.getValue().getLatitude());
        assertNull(captor.getValue().getLongitude());
        assertNull(result.getAffiliationType());
        assertNull(result.getLatitude());
        assertNull(result.getLongitude());
        assertEquals("浅草寺", result.getTranslations().get("ja").getName());
        verify(enrichmentService).createJob(EnrichmentResourceType.TEMPLE, result.getId());
    }

    @Test
    void createTemple_WithAffiliationTypeAndCoordinates_ShouldPersistAndReturnThem() {
        // Arrange
        TempleCreate templeCreate = new TempleCreate();
        templeCreate.setAffiliationType(AffiliationType.BUDDHIST);
        templeCreate.setLatitude(35.7148);
        templeCreate.setLongitude(139.7967);
        when(templeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Temple result = templeService.createTemple(templeCreate);

        // Assert
        ArgumentCaptor<TempleEntity> captor = ArgumentCaptor.forClass(TempleEntity.class);
        verify(templeRepository).save(captor.capture());
        assertEquals("buddhist", captor.getValue().getAffiliationType());
        assertEquals(AffiliationType.BUDDHIST, result.getAffiliationType());
        assertEquals(35.7148, result.getLatitude());
        assertEquals(139.7967, result.getLongitude());
    }

    @Test
    void searchTemples_WithTempleLackingAffiliationTypeAndCoordinates_ShouldMapWithoutError() {
        // Arrange: a temple that was created by name only and has not been enriched yet
        TempleEntity unenriched = new TempleEntity();
        unenriched.setId(UUID.randomUUID());
        when(templeRepository.findAll(ArgumentMatchers.<Specification<TempleEntity>>any())).thenReturn(List.of(unenriched));

        // Act
        List<Temple> result = templeService.searchTemples(null, null, null, null, null);

        // Assert
        assertEquals(1, result.size());
        assertNull(result.getFirst().getAffiliationType());
        assertNull(result.getFirst().getLatitude());
        assertNull(result.getFirst().getLongitude());
    }

    @Test
    void resolveTemple_WithTempleDtoWithoutAffiliationType_ShouldCreateNewTemple() {
        // Arrange
        GoshuinCreateTemple templeDto = new GoshuinCreateTemple();
        templeDto.setName("New Temple");
        when(templeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        TempleEntity result = templeService.resolveTemple(null, templeDto, "en");

        // Assert
        assertNull(result.getAffiliationType());
    }

    @Test
    void resolveTemple_WithTempleId_ShouldReturnExistingTemple() {
        // Arrange
        UUID templeId = UUID.randomUUID();
        TempleEntity templeEntity = new TempleEntity();
        templeEntity.setId(templeId);
        when(templeRepository.findById(templeId)).thenReturn(java.util.Optional.of(templeEntity));

        // Act
        TempleEntity result = templeService.resolveTemple(templeId, null, null);

        // Assert
        assertEquals(templeEntity, result);
    }

    @Test
    void resolveTemple_WithTempleDto_ShouldCreateNewTemple() {
        // Arrange
        GoshuinCreateTemple templeDto = new GoshuinCreateTemple();
        templeDto.setName("New Temple");
        templeDto.setCity("Tokyo");
        templeDto.setAffiliationType(AffiliationType.SHINTO);

        TempleEntity templeEntity = new TempleEntity();
        UUID templeId = UUID.randomUUID();
        ReflectionTestUtils.setField(templeEntity, "id", templeId);
        when(templeRepository.save(any())).thenReturn(templeEntity);

        // Act
        TempleEntity result = templeService.resolveTemple(null, templeDto, "ja");

        // Assert
        assertNotNull(result);
        verify(templeRepository).save(any());
        verify(enrichmentService).createJob(EnrichmentResourceType.TEMPLE, templeId);
    }

    @Test
    void resolveTemple_WithNeither_ShouldThrowException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                templeService.resolveTemple(null, null, null)
        );
    }

    @Test
    void resolveTemple_WithBoth_ShouldThrowException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                templeService.resolveTemple(UUID.randomUUID(), new GoshuinCreateTemple(), null)
        );
    }
}
