package io.github.peterberghuis.goshuin.controller;

import io.github.peterberghuis.goshuin.api.GoshuinApi;
import io.github.peterberghuis.goshuin.dto.Goshuin;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

@RestController
public class GoshuinController implements GoshuinApi {

    @Override
    public ResponseEntity<List<Goshuin>> getGoshuins() {
        return ResponseEntity.ok(Collections.emptyList());
    }
}
