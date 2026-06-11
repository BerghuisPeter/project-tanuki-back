package io.github.peterberghuis.goshuin.repository;

import io.github.peterberghuis.goshuin.entity.GoshuinEntity;
import io.github.peterberghuis.goshuin.entity.TempleEntity;
import io.github.peterberghuis.goshuin.model.ProximityCursor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class GoshuinRepositoryCustomImpl implements GoshuinRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<GoshuinEntity> findAllByDistance(
            Specification<GoshuinEntity> spec,
            double lat,
            double lon,
            int limit,
            ProximityCursor cursor) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<GoshuinEntity> cq = cb.createQuery(GoshuinEntity.class);
        Root<GoshuinEntity> root = cq.from(GoshuinEntity.class);

        Predicate specPredicate = spec != null
                ? spec.toPredicate(root, cq, cb)
                : cb.conjunction();

        Join<GoshuinEntity, TempleEntity> temple = root.join("temple");

        Expression<Double> templeLat = temple.get("latitude").as(Double.class);
        Expression<Double> templeLon = temple.get("longitude").as(Double.class);

        Expression<Double> latRad = cb.function("radians", Double.class, templeLat);
        Expression<Double> lonRad = cb.function("radians", Double.class, templeLon);
        Expression<Double> latRad0 = cb.literal(Math.toRadians(lat));
        Expression<Double> lonRad0 = cb.literal(Math.toRadians(lon));

        Expression<Double> cosPart = cb.prod(
                cb.function("cos", Double.class, latRad0),
                cb.prod(
                        cb.function("cos", Double.class, latRad),
                        cb.function("cos", Double.class, cb.diff(lonRad, lonRad0))
                )
        );

        Expression<Double> sinPart = cb.prod(
                cb.function("sin", Double.class, latRad0),
                cb.function("sin", Double.class, latRad)
        );

        Expression<Double> acosArg = cb.function("greatest", Double.class,
                cb.literal(-1.0),
                cb.function("least", Double.class,
                        cb.literal(1.0),
                        cb.sum(cosPart, sinPart)
                )
        );

        Expression<Double> distance = cb.prod(
                cb.literal(6371.0),
                cb.function("acos", Double.class, acosArg)
        );

        cq.where(specPredicate);
        cq.orderBy(cb.asc(distance), cb.asc(root.get("id")));

        return entityManager.createQuery(cq)
                .setFirstResult(cursor != null ? cursor.offset() : 0)
                .setMaxResults(limit + 1)
                .getResultList();
    }
}