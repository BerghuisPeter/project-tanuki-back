package io.github.peterberghuis.goshuin.controller;

import io.github.peterberghuis.goshuin.api.TempleApi;
import io.github.peterberghuis.goshuin.dto.AffiliationType;
import io.github.peterberghuis.goshuin.dto.EnrichmentStatus;
import io.github.peterberghuis.goshuin.dto.Temple;
import io.github.peterberghuis.goshuin.dto.TempleCreate;
import io.github.peterberghuis.goshuin.service.TempleService;
import io.github.peterberghuis.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class TempleController implements TempleApi {

    private final TempleService templeService;

    @Override
    public ResponseEntity<List<Temple>> searchTemples(String name, String city, AffiliationType affiliationType, Boolean mine, List<EnrichmentStatus> enrichmentStatuses) {
        UUID userId = (mine != null && mine) ? SecurityUtils.getUserIdFromContext() : null;
        List<EnrichmentStatus> statusesToUse = (mine != null && mine) ? enrichmentStatuses : null;
        return ResponseEntity.ok(templeService.searchTemples(name, city, affiliationType, userId, statusesToUse));
    }

    @Override
    public ResponseEntity<Temple> addTemple(TempleCreate templeCreate) {
        return ResponseEntity.status(HttpStatus.CREATED).body(templeService.createTemple(templeCreate));
    }
}
