package io.github.peterberghuis.goshuin.repository;

import io.github.peterberghuis.goshuin.dto.AffiliationType;
import io.github.peterberghuis.goshuin.entity.EnrichmentStatus;
import io.github.peterberghuis.goshuin.entity.GoshuinEntity;
import io.github.peterberghuis.goshuin.entity.TempleEntity;
import io.github.peterberghuis.goshuin.entity.TempleI18nEntity;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TempleSpecifications {

    public static Specification<TempleEntity> search(
            String name,
            String city,
            AffiliationType affiliationType,
            UUID userId,
            List<io.github.peterberghuis.goshuin.dto.EnrichmentStatus> enrichmentStatuses
    ) {
        return (root, cq, cb) -> {

            if (!Long.class.equals(cq.getResultType())) {
                cq.distinct(true);
            }

            Join<TempleEntity, TempleI18nEntity> translation = root.join("translations");

            List<Predicate> predicates = new ArrayList<>();

            if (name != null && !name.isBlank()) {
                predicates.add(cb.like(
                        cb.lower(translation.get("name")),
                        "%" + name.toLowerCase() + "%"
                ));
            }

            if (city != null && !city.isBlank()) {
                predicates.add(cb.like(
                        cb.lower(translation.get("city")),
                        "%" + city.toLowerCase() + "%"
                ));
            }

            if (affiliationType != null) {
                predicates.add(
                        cb.equal(root.get("affiliationType"), affiliationType.getValue())
                );
            }

            if (userId != null) {
                Subquery<UUID> subquery = cq.subquery(UUID.class);
                Root<GoshuinEntity> goshuinRoot = subquery.from(GoshuinEntity.class);
                subquery.select(goshuinRoot.get("temple").get("id"))
                        .where(cb.equal(goshuinRoot.get("userId"), userId));
                predicates.add(root.get("id").in(subquery));
            }

            if (enrichmentStatuses == null || enrichmentStatuses.isEmpty()) {
                predicates.add(cb.equal(root.get("enrichment").get("status"), EnrichmentStatus.COMPLETE));
            } else {
                List<EnrichmentStatus> entityStatuses = enrichmentStatuses.stream()
                        .map(s -> EnrichmentStatus.valueOf(s.name()))
                        .toList();
                predicates.add(root.get("enrichment").get("status").in(entityStatuses));
            }

            return predicates.isEmpty()
                    ? null
                    : cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
