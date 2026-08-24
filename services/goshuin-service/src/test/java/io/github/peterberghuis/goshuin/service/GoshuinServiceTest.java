package io.github.peterberghuis.goshuin.service;

import com.google.cloud.storage.Storage;
import io.github.peterberghuis.common.dto.UploadUrlResponse;
import io.github.peterberghuis.gcp.config.GcpStorageProperties;
import io.github.peterberghuis.goshuin.client.ProfileClient;
import io.github.peterberghuis.goshuin.config.GoshuinImageStorageProperties;
import io.github.peterberghuis.goshuin.dto.*;
import io.github.peterberghuis.goshuin.entity.EnrichmentResourceType;
import io.github.peterberghuis.goshuin.entity.GoshuinEntity;
import io.github.peterberghuis.goshuin.entity.TempleEntity;
import io.github.peterberghuis.goshuin.mapper.GoshuinMapper;
import io.github.peterberghuis.goshuin.repository.GoshuinRepository;
import io.github.peterberghuis.goshuin.repository.GoshuinRepositoryCustom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoshuinServiceTest {

    @Mock
    private Storage storage;

    @Mock
    private GoshuinRepository goshuinRepository;

    @Mock
    private GoshuinRepositoryCustom goshuinRepositoryCustom;

    @Mock
    private TempleService templeService;

    @Mock
    private EnrichmentService enrichmentService;

    @Mock
    private ProfileClient profileClient;

    @Mock
    private GoshuinMapper goshuinMapper;

    @InjectMocks
    private GoshuinService goshuinService;

    private GcpStorageProperties gcpStorageProperties;
    private GoshuinImageStorageProperties goshuinImageStorageProperties;

    @BeforeEach
    void setUp() {
        gcpStorageProperties = new GcpStorageProperties();
        gcpStorageProperties.setBucketName("test-bucket");

        goshuinImageStorageProperties = new GoshuinImageStorageProperties();
        goshuinImageStorageProperties.setAllowedContentTypes(List.of("image/jpeg", "image/png"));
        goshuinImageStorageProperties.setMaxSizeBytes(5242880L);

        ReflectionTestUtils.setField(goshuinService, "gcpStorageProperties", gcpStorageProperties);
        ReflectionTestUtils.setField(goshuinService, "goshuinImageStorageProperties", goshuinImageStorageProperties);
    }

    @Test
    void getGoshuinUploadUrl_WithValidContentType_ShouldReturnUrl() throws Exception {
        // Arrange
        UUID userId = UUID.randomUUID();
        String contentType = "image/jpeg";
        URL mockUrl = new URL("https://storage.googleapis.com/test-bucket/goshuins/test");
        when(storage.signUrl(any(), anyLong(), any(), any(), any(), any())).thenReturn(mockUrl);

        // Act
        UploadUrlResponse result = goshuinService.getGoshuinUploadUrl(userId, contentType);

        // Assert
        assertNotNull(result);
        assertEquals(mockUrl.toString(), result.getUploadUrl());
        assertTrue(result.getFileName().startsWith("goshuins/" + userId));
        assertEquals(5242880L, result.getMaxSizeBytes());
        assertEquals(List.of("image/jpeg", "image/png"), result.getAllowedContentTypes());
    }

    @Test
    void getGoshuinUploadUrl_WithInvalidContentType_ShouldThrowException() {
        // Arrange
        UUID userId = UUID.randomUUID();
        String contentType = "application/pdf";

        // Act & Assert
        assertThrows(ResponseStatusException.class, () ->
                goshuinService.getGoshuinUploadUrl(userId, contentType)
        );
    }

    @Test
    void createGoshuin_ShouldCreateEnrichmentJob() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID templeId = UUID.randomUUID();
        GoshuinCreate goshuinCreate = new GoshuinCreate();
        goshuinCreate.setTempleId(templeId);
        goshuinCreate.setImageUrls(List.of(URI.create("https://example.com/image.jpg")));
        goshuinCreate.setFormat(GoshuinFormat.WRITTEN);

        TempleEntity templeEntity = new TempleEntity();
        templeEntity.setId(templeId);

        GoshuinEntity goshuinEntity = new GoshuinEntity();
        UUID goshuinId = UUID.randomUUID();
        ReflectionTestUtils.setField(goshuinEntity, "id", goshuinId);

        when(templeService.resolveTemple(eq(templeId), any(), any())).thenReturn(templeEntity);
        when(goshuinRepository.saveAndFlush(any())).thenReturn(goshuinEntity);
        when(goshuinMapper.mapToDto(any(), any())).thenReturn(new Goshuin());

        // Act
        goshuinService.createGoshuin(userId, goshuinCreate);

        // Assert
        verify(goshuinRepository).saveAndFlush(any());
        verify(enrichmentService).createJob(EnrichmentResourceType.GOSHUIN, goshuinId);
    }

    @Test
    void createGoshuin_WithNewTemple_ShouldCreateTempleAndJobs() {
        // Arrange
        UUID userId = UUID.randomUUID();
        GoshuinCreate goshuinCreate = new GoshuinCreate();
        GoshuinCreateTemple newTemple = new GoshuinCreateTemple();
        newTemple.setName("New Temple");
        newTemple.setCity("Tokyo");
        newTemple.setAffiliationType(AffiliationType.SHINTO);
        goshuinCreate.setTemple(newTemple);
        goshuinCreate.setImageUrls(List.of(URI.create("https://example.com/image.jpg")));
        goshuinCreate.setFormat(GoshuinFormat.WRITTEN);
        goshuinCreate.setOriginalLocale("ja");

        TempleEntity templeEntity = new TempleEntity();
        UUID templeId = UUID.randomUUID();
        ReflectionTestUtils.setField(templeEntity, "id", templeId);

        GoshuinEntity goshuinEntity = new GoshuinEntity();
        UUID goshuinId = UUID.randomUUID();
        ReflectionTestUtils.setField(goshuinEntity, "id", goshuinId);

        when(templeService.resolveTemple(any(), any(GoshuinCreateTemple.class), eq("ja"))).thenReturn(templeEntity);
        when(goshuinRepository.saveAndFlush(any())).thenReturn(goshuinEntity);
        when(goshuinMapper.mapToDto(any(), any())).thenReturn(new Goshuin());

        // Act
        goshuinService.createGoshuin(userId, goshuinCreate);

        // Assert
        verify(templeService).resolveTemple(any(), eq(newTemple), eq("ja"));
        verify(goshuinRepository).saveAndFlush(any());
        verify(enrichmentService).createJob(EnrichmentResourceType.GOSHUIN, goshuinId);
    }
}
