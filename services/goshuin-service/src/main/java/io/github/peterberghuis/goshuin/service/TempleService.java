package io.github.peterberghuis.goshuin.service;

import io.github.peterberghuis.goshuin.dto.Temple;
import io.github.peterberghuis.goshuin.dto.TempleCreate;

import java.util.List;

public interface TempleService {
    List<Temple> getAllTemples();

    Temple createTemple(TempleCreate templeCreate);
}
