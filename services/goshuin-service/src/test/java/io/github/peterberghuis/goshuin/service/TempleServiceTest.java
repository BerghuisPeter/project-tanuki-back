package io.github.peterberghuis.goshuin.service;

import io.github.peterberghuis.goshuin.dto.AffiliationType;
import io.github.peterberghuis.goshuin.dto.GoshuinCreateTemple;
import io.github.peterberghuis.goshuin.dto.TempleCreate;
import io.github.peterberghuis.goshuin.entity.EnrichmentResourceType;
import io.github.peterberghuis.goshuin.entity.TempleEntity;
import io.github.peterberghuis.goshuin.repository.TempleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
