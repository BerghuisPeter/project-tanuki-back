package io.github.peterberghuis.goshuin.service;

import io.github.peterberghuis.goshuin.entity.EnrichmentJobEntity;
import io.github.peterberghuis.goshuin.entity.EnrichmentResourceType;
import io.github.peterberghuis.goshuin.entity.EnrichmentStatus;
import io.github.peterberghuis.goshuin.event.EnrichmentJobCreatedEvent;
import io.github.peterberghuis.goshuin.repository.EnrichmentJobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrichmentServiceTest {

    @Mock
    private EnrichmentJobRepository enrichmentJobRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private EnrichmentService enrichmentService;

    @Test
    void createJob_ShouldSaveJobAndPublishEvent() {
        // Arrange
        UUID resourceId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        EnrichmentResourceType resourceType = EnrichmentResourceType.GOSHUIN;

        when(enrichmentJobRepository.save(any(EnrichmentJobEntity.class))).thenAnswer(invocation -> {
            EnrichmentJobEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", jobId);
            return entity;
        });

        // Act
        EnrichmentJobEntity result = enrichmentService.createJob(resourceType, resourceId);

        // Assert
        assertNotNull(result);
        assertEquals(jobId, result.getId());
        assertEquals(resourceType, result.getResourceType());
        assertEquals(resourceId, result.getResourceId());
        assertEquals(EnrichmentStatus.PENDING, result.getStatus());

        ArgumentCaptor<EnrichmentJobCreatedEvent> eventCaptor = ArgumentCaptor.forClass(EnrichmentJobCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        EnrichmentJobCreatedEvent capturedEvent = eventCaptor.getValue();
        assertEquals(jobId, capturedEvent.jobId());
        assertEquals(resourceType, capturedEvent.resourceType());
        assertEquals(resourceId, capturedEvent.resourceId());
    }
}
