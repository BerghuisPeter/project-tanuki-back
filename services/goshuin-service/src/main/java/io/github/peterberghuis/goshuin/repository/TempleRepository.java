package io.github.peterberghuis.goshuin.repository;

import io.github.peterberghuis.goshuin.entity.TempleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TempleRepository extends JpaRepository<TempleEntity, UUID> {
}
