package io.github.peterberghuis.goshuin.controller;

import io.github.peterberghuis.goshuin.internal.api.InternalGoshuinApi;
import io.github.peterberghuis.goshuin.internal.dto.GoshuinEnrichmentUpdate;
import io.github.peterberghuis.goshuin.internal.dto.InternalGoshuin;
import io.github.peterberghuis.goshuin.service.InternalEnrichmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class InternalGoshuinController implements InternalGoshuinApi {

    private final InternalEnrichmentService internalEnrichmentService;

    @Override
    public ResponseEntity<InternalGoshuin> getInternalGoshuin(UUID id) {
        return ResponseEntity.ok(internalEnrichmentService.getGoshuin(id));
    }

    @Override
    public ResponseEntity<Void> updateInternalGoshuinEnrichment(UUID id, GoshuinEnrichmentUpdate goshuinEnrichmentUpdate) {
        internalEnrichmentService.updateGoshuin(id, goshuinEnrichmentUpdate);
        return ResponseEntity.noContent().build();
    }
}
