package io.github.peterberghuis.goshuin.service;

import com.google.cloud.storage.Storage;
import io.github.peterberghuis.common.dto.UploadUrlResponse;
import io.github.peterberghuis.gcp.config.GcpStorageProperties;
import io.github.peterberghuis.goshuin.config.GoshuinImageStorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.net.URL;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoshuinServiceTest {

    @Mock
    private Storage storage;

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
}
