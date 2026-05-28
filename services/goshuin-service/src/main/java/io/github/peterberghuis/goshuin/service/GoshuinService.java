package io.github.peterberghuis.goshuin.service;

import io.github.peterberghuis.goshuin.dto.Goshuin;

import java.util.List;

public interface GoshuinService {
    List<Goshuin> getAllGoshuins();
}
