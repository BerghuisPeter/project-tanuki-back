package io.github.peterberghuis.gcp.pubsub;

import com.google.api.core.ApiFuture;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import io.github.peterberghuis.gcp.config.GcpPubSubProperties;
import io.github.peterberghuis.gcp.config.GcpStorageProperties;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class PubSubPublisherService {

    private final GcpPubSubProperties pubSubProperties;
    private final GcpStorageProperties storageProperties;
    private final Map<String, Publisher> publishers = new ConcurrentHashMap<>();
    private volatile ManagedChannel emulatorChannel;

    public PubSubPublisherService(GcpPubSubProperties pubSubProperties,
                                  @Autowired(required = false) GcpStorageProperties storageProperties) {
        this.pubSubProperties = pubSubProperties;
        this.storageProperties = storageProperties;
    }

    public String publish(String topicNameOrId, String messageData) {
        return publish(topicNameOrId, messageData, Map.of());
    }

    public String publish(String topicNameOrId, String messageData, Map<String, String> attributes) {
        if (!pubSubProperties.isEnabled()) {
            log.info("Pub/Sub publishing is disabled. Skipping message for topic: {}", topicNameOrId);
            return null;
        }

        String projectId = pubSubProperties.getProjectId();
        if (projectId == null || projectId.isBlank()) {
            log.warn("GCP projectId is not configured for Pub/Sub. Skipping message: {}", messageData);
            return null;
        }

        try {
            Publisher publisher = getOrCreatePublisher(topicNameOrId);
            PubsubMessage.Builder messageBuilder = PubsubMessage.newBuilder()
                    .setData(ByteString.copyFromUtf8(messageData));

            if (attributes != null && !attributes.isEmpty()) {
                messageBuilder.putAllAttributes(attributes);
            }

            PubsubMessage pubsubMessage = messageBuilder.build();
            ApiFuture<String> future = publisher.publish(pubsubMessage);
            String messageId = future.get(10, TimeUnit.SECONDS);
            log.info("Successfully published message to topic {} with messageId: {}", topicNameOrId, messageId);
            return messageId;
        } catch (Exception e) {
            log.error("Failed to publish message to Pub/Sub topic {}: {}", topicNameOrId, e.getMessage(), e);
            throw new RuntimeException("Failed to publish message to Pub/Sub topic " + topicNameOrId, e);
        }
    }

    private Publisher getOrCreatePublisher(String topicNameOrId) throws IOException {
        Publisher publisher = publishers.get(topicNameOrId);
        if (publisher != null) {
            return publisher;
        }

        synchronized (publishers) {
            publisher = publishers.get(topicNameOrId);
            if (publisher != null) {
                return publisher;
            }

            TopicName topicName;
            if (topicNameOrId.startsWith("projects/")) {
                topicName = TopicName.parse(topicNameOrId);
            } else {
                topicName = TopicName.of(pubSubProperties.getProjectId(), topicNameOrId);
            }

            Publisher.Builder builder = Publisher.newBuilder(topicName);

            String emulatorHost = pubSubProperties.getEmulatorHost();
            String credentialsJson = pubSubProperties.getCredentialsJson();
            if ((credentialsJson == null || credentialsJson.isBlank()) && storageProperties != null) {
                credentialsJson = storageProperties.getCredentialsJson();
            }

            if (emulatorHost != null && !emulatorHost.isBlank()) {
                // The Java client does not pick up PUBSUB_EMULATOR_HOST by itself
                if (emulatorChannel == null) {
                    emulatorChannel = ManagedChannelBuilder.forTarget(emulatorHost).usePlaintext().build();
                }
                builder.setChannelProvider(FixedTransportChannelProvider.create(GrpcTransportChannel.create(emulatorChannel)));
                builder.setCredentialsProvider(NoCredentialsProvider.create());
                log.info("Using Pub/Sub emulator at {} for topic {}", emulatorHost, topicName);
            } else if (credentialsJson != null && !credentialsJson.trim().isEmpty()) {
                GoogleCredentials credentials = GoogleCredentials.fromStream(
                        new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8)));
                CredentialsProvider credentialsProvider = FixedCredentialsProvider.create(credentials);
                builder.setCredentialsProvider(credentialsProvider);
            }

            Publisher newPublisher = builder.build();
            publishers.put(topicNameOrId, newPublisher);
            return newPublisher;
        }
    }

    @PreDestroy
    public void cleanup() {
        publishers.values().forEach(publisher -> {
            try {
                publisher.shutdown();
                publisher.awaitTermination(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Error shutting down Pub/Sub publisher: {}", e.getMessage());
            }
        });
        publishers.clear();

        if (emulatorChannel != null) {
            try {
                emulatorChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Error shutting down Pub/Sub emulator channel: {}", e.getMessage());
            }
            emulatorChannel = null;
        }
    }
}
