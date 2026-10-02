package io.github.peterberghuis.goshuin.controller;

import io.github.peterberghuis.goshuin.internal.api.InternalTempleApi;
import io.github.peterberghuis.goshuin.internal.dto.InternalTemple;
import io.github.peterberghuis.goshuin.internal.dto.TempleEnrichmentUpdate;
import io.github.peterberghuis.goshuin.service.InternalEnrichmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class InternalTempleController implements InternalTempleApi {

    private final InternalEnrichmentService internalEnrichmentService;

    @Override
    public ResponseEntity<InternalTemple> getInternalTemple(UUID id) {
        return ResponseEntity.ok(internalEnrichmentService.getTemple(id));
    }

    @Override
    public ResponseEntity<Void> updateInternalTempleEnrichment(UUID id, TempleEnrichmentUpdate templeEnrichmentUpdate) {
        internalEnrichmentService.updateTemple(id, templeEnrichmentUpdate);
        return ResponseEntity.noContent().build();
    }
}
