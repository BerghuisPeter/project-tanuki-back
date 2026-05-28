package io.github.peterberghuis.goshuin.service;

import io.github.peterberghuis.goshuin.dto.Goshuin;
import io.github.peterberghuis.goshuin.dto.GoshuinCreate;

import java.util.List;

public interface GoshuinService {
    List<Goshuin> getAllGoshuins();

    Goshuin createGoshuin(GoshuinCreate goshuinCreate);
}
