package io.github.peterberghuis.goshuin.controller;

import io.github.peterberghuis.goshuin.api.TempleApi;
import io.github.peterberghuis.goshuin.dto.Temple;
import io.github.peterberghuis.goshuin.dto.TempleCreate;
import io.github.peterberghuis.goshuin.service.TempleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class TempleController implements TempleApi {

    private final TempleService templeService;

    @Override
    public ResponseEntity<List<Temple>> searchTemples(String query) {
        return ResponseEntity.ok(templeService.searchTemples(query));
    }

    @Override
    public ResponseEntity<Temple> addTemple(TempleCreate templeCreate) {
        return ResponseEntity.status(HttpStatus.CREATED).body(templeService.createTemple(templeCreate));
    }
}
