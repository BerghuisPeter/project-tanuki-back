package io.github.peterberghuis.goshuin.repository;

import io.github.peterberghuis.goshuin.entity.GoshuinCommentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GoshuinCommentRepository extends JpaRepository<GoshuinCommentEntity, UUID> {

    List<GoshuinCommentEntity> findAllByGoshuinId(UUID goshuinId);
}
