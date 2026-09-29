package io.github.peterberghuis.goshuin.event;

import io.github.peterberghuis.goshuin.entity.EnrichmentResourceType;

import java.util.UUID;

public record EnrichmentJobCreatedEvent(
        UUID jobId,
        EnrichmentResourceType resourceType,
        UUID resourceId
) {
}
