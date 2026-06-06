package io.github.peterberghuis.goshuin.repository;

import io.github.peterberghuis.goshuin.entity.GoshuinEntity;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

public interface GoshuinRepositoryCustom {
    List<GoshuinEntity> findAllByDistance(
            Specification<GoshuinEntity> spec,
            double lat,
            double lon);
}