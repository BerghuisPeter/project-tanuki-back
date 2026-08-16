package io.github.peterberghuis.goshuin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Embeddable
@Getter
@Setter
public class EnrichmentMetadata {

    @Enumerated(EnumType.STRING)
    @Column(name = "enrichment_status", nullable = false)
    private EnrichmentStatus status = EnrichmentStatus.PENDING;

    @Column(name = "enrichment_error")
    private String error;

    @Column(name = "enrichment_attempts", nullable = false)
    private Integer attempts = 0;

    @Column(name = "last_enrichment_at")
    private OffsetDateTime lastAt;
}
