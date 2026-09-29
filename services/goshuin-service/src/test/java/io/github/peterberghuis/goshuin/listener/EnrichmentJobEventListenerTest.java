package io.github.peterberghuis.goshuin.listener;

import io.github.peterberghuis.gcp.config.GcpPubSubProperties;
import io.github.peterberghuis.gcp.pubsub.PubSubPublisherService;
import io.github.peterberghuis.goshuin.entity.EnrichmentResourceType;
import io.github.peterberghuis.goshuin.event.EnrichmentJobCreatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrichmentJobEventListenerTest {

    @Mock
    private PubSubPublisherService pubSubPublisherService;

    @Mock
    private GcpPubSubProperties pubSubProperties;

    @InjectMocks
    private EnrichmentJobEventListener enrichmentJobEventListener;

    @Test
    void onEnrichmentJobCreated_ShouldPublishMessageToPubSub() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        EnrichmentResourceType resourceType = EnrichmentResourceType.GOSHUIN;
        EnrichmentJobCreatedEvent event = new EnrichmentJobCreatedEvent(jobId, resourceType, resourceId);

        when(pubSubProperties.getTopicName()).thenReturn("enrichment-requests");

        // Act
        enrichmentJobEventListener.onEnrichmentJobCreated(event);

        // Assert
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> attributesCaptor = ArgumentCaptor.forClass(Map.class);

        verify(pubSubPublisherService).publish(topicCaptor.capture(), payloadCaptor.capture(), attributesCaptor.capture());

        assertEquals("enrichment-requests", topicCaptor.getValue());
        assertTrue(payloadCaptor.getValue().contains(jobId.toString()));
        assertTrue(payloadCaptor.getValue().contains(resourceType.name()));
        assertTrue(payloadCaptor.getValue().contains(resourceId.toString()));

        Map<String, String> attributes = attributesCaptor.getValue();
        assertEquals(jobId.toString(), attributes.get("jobId"));
        assertEquals(resourceType.name(), attributes.get("resourceType"));
        assertEquals(resourceId.toString(), attributes.get("resourceId"));
    }
}
