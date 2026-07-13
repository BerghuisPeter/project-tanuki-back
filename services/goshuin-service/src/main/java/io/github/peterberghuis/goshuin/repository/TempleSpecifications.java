package io.github.peterberghuis.goshuin.repository;

import io.github.peterberghuis.goshuin.dto.AffiliationType;
import io.github.peterberghuis.goshuin.entity.TempleEntity;
import io.github.peterberghuis.goshuin.entity.TempleI18nEntity;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TempleSpecifications {

    public static Specification<TempleEntity> search(
            String name,
            String city,
            AffiliationType affiliationType
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

            return predicates.isEmpty()
                    ? null
                    : cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
