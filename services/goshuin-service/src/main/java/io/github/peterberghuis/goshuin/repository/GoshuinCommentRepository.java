package io.github.peterberghuis.goshuin.repository;

import io.github.peterberghuis.goshuin.entity.GoshuinCommentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GoshuinCommentRepository extends JpaRepository<GoshuinCommentEntity, UUID> {

    @Query("SELECT gc.goshuin.id, COUNT(gc) FROM GoshuinCommentEntity gc WHERE gc.goshuin.id IN :goshuinIds GROUP BY gc.goshuin.id")
    List<Object[]> countCommentsByGoshuinIds(@Param("goshuinIds") List<UUID> goshuinIds);
}
