package io.github.peterberghuis.goshuin.controller;

import io.github.peterberghuis.goshuin.service.EnrichmentService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/enrich")
@RequiredArgsConstructor
@Hidden
public class EnrichmentController {

    private final EnrichmentService enrichmentService;

    @GetMapping("/goshuin")
    public ResponseEntity<Void> enrichGoshuin(UUID goshuinId) {
        enrichmentService.enrich(goshuinId);
        return ResponseEntity.ok().build();
    }
}
