package io.github.peterberghuis.goshuin.service;

import io.github.peterberghuis.goshuin.entity.EnrichmentJobEntity;
import io.github.peterberghuis.goshuin.entity.EnrichmentResourceType;
import io.github.peterberghuis.goshuin.entity.EnrichmentStatus;
import io.github.peterberghuis.goshuin.event.EnrichmentJobCreatedEvent;
import io.github.peterberghuis.goshuin.repository.EnrichmentJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnrichmentService {

    private final EnrichmentJobRepository enrichmentJobRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public EnrichmentJobEntity createJob(EnrichmentResourceType resourceType, UUID resourceId) {
        EnrichmentJobEntity job = new EnrichmentJobEntity();
        job.setResourceType(resourceType);
        job.setResourceId(resourceId);
        job.setStatus(EnrichmentStatus.PENDING);
        EnrichmentJobEntity savedJob = enrichmentJobRepository.save(job);
        log.info("Created enrichment job: id={}, resourceType={}, resourceId={}",
                savedJob.getId(), resourceType, resourceId);

        eventPublisher.publishEvent(new EnrichmentJobCreatedEvent(
                savedJob.getId(),
                savedJob.getResourceType(),
                savedJob.getResourceId()
        ));

        return savedJob;
    }
}
