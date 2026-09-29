package io.github.peterberghuis.gcp.pubsub;

import io.github.peterberghuis.gcp.config.GcpPubSubProperties;
import io.github.peterberghuis.gcp.config.GcpStorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

class PubSubPublisherServiceTest {

    private GcpPubSubProperties pubSubProperties;
    private GcpStorageProperties storageProperties;
    private PubSubPublisherService publisherService;

    @BeforeEach
    void setUp() {
        pubSubProperties = new GcpPubSubProperties();
        storageProperties = new GcpStorageProperties();
        publisherService = new PubSubPublisherService(pubSubProperties, storageProperties);
    }

    @Test
    void publish_WhenDisabled_ShouldReturnNullAndNotFail() {
        pubSubProperties.setEnabled(false);
        String result = publisherService.publish("enrichment-requests", "{\"jobId\":\"test\"}");
        assertNull(result);
    }

    @Test
    void publish_WhenProjectIdMissing_ShouldReturnNullAndNotFail() {
        pubSubProperties.setEnabled(true);
        pubSubProperties.setProjectId(null);
        String result = publisherService.publish("enrichment-requests", "{\"jobId\":\"test\"}");
        assertNull(result);
    }
}
