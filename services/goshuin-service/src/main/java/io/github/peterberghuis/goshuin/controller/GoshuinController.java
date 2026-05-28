package io.github.peterberghuis.goshuin.controller;

import io.github.peterberghuis.goshuin.api.GoshuinApi;
import io.github.peterberghuis.goshuin.dto.Goshuin;
import io.github.peterberghuis.goshuin.dto.GoshuinCreate;
import io.github.peterberghuis.goshuin.service.GoshuinService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class GoshuinController implements GoshuinApi {

    private final GoshuinService goshuinService;

    @Override
    public ResponseEntity<List<Goshuin>> getGoshuins() {
        return ResponseEntity.ok(goshuinService.getAllGoshuins());
    }

    @Override
    public ResponseEntity<Goshuin> addGoshuin(GoshuinCreate goshuinCreate) {
        return ResponseEntity.status(HttpStatus.CREATED).body(goshuinService.createGoshuin(goshuinCreate));
    }
}
