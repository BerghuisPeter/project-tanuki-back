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
        CriteriaQuery<GoshuinEntity> query = cb.createQuery(GoshuinEntity.class);

        Root<GoshuinEntity> root = query.from(GoshuinEntity.class);

        Predicate specPredicate = spec != null
                ? spec.toPredicate(root, query, cb)
                : cb.conjunction();

        Join<GoshuinEntity, TempleEntity> temple = root.join("temple");

        Expression<Double> latRad =
                cb.function("radians", Double.class, temple.get("latitude"));

        Expression<Double> lonRad =
                cb.function("radians", Double.class, temple.get("longitude"));

        double latRadValue = Math.toRadians(lat);
        double lonRadValue = Math.toRadians(lon);

        Expression<Double> cosPart = cb.prod(
                cb.function("cos", Double.class, cb.literal(latRadValue)),
                cb.prod(
                        cb.function("cos", Double.class, latRad),
                        cb.function(
                                "cos",
                                Double.class,
                                cb.diff(lonRad, cb.literal(lonRadValue))
                        )
                )
        );

        Expression<Double> sinPart = cb.prod(
                cb.function("sin", Double.class, cb.literal(latRadValue)),
                cb.function("sin", Double.class, latRad)
        );

        Expression<Double> acosArg = cb.sum(cosPart, sinPart);

        Expression<Double> distance = cb.prod(
                cb.literal(6371.0),
                cb.function("acos", Double.class, acosArg)
        );

        if (specPredicate != null) {
            query.where(specPredicate);
        }

        query.orderBy(cb.asc(distance), cb.asc(root.get("id")));

        return entityManager.createQuery(query)
                .setFirstResult(cursor != null ? cursor.offset() : 0)
                .setMaxResults(limit + 1)
                .getResultList();
    }
}