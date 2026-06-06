package io.github.peterberghuis.goshuin.repository;

import io.github.peterberghuis.goshuin.entity.GoshuinEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GoshuinRepository extends JpaRepository<GoshuinEntity, UUID>, JpaSpecificationExecutor<GoshuinEntity> {
    @Override
    @EntityGraph(attributePaths = {
            "translations",
            "images",
            "temple",
            "temple.translations"
    })
    List<GoshuinEntity> findAll(
            Specification<GoshuinEntity> spec,
            Sort sort
    );
}
