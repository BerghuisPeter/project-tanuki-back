package io.github.peterberghuis.goshuin.service;

import io.github.peterberghuis.goshuin.entity.EnrichmentJobEntity;
import io.github.peterberghuis.goshuin.entity.EnrichmentResourceType;
import io.github.peterberghuis.goshuin.entity.EnrichmentStatus;
import io.github.peterberghuis.goshuin.repository.EnrichmentJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EnrichmentService {

    private final EnrichmentJobRepository enrichmentJobRepository;

    @Transactional
    public void createJob(EnrichmentResourceType resourceType, UUID resourceId) {
        EnrichmentJobEntity job = new EnrichmentJobEntity();
        job.setResourceType(resourceType);
        job.setResourceId(resourceId);
        job.setStatus(EnrichmentStatus.PENDING);
        enrichmentJobRepository.save(job);
    }

    public void enrich(UUID goshuinId) {
        // retreive the
        return;
    }
}
