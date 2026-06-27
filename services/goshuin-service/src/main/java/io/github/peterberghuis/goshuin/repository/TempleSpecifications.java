package io.github.peterberghuis.goshuin.repository;

import io.github.peterberghuis.goshuin.entity.TempleEntity;
import io.github.peterberghuis.goshuin.entity.TempleI18nEntity;
import jakarta.persistence.criteria.Join;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TempleSpecifications {

    public static Specification<TempleEntity> search(String query) {
        return (root, cq, cb) -> {
            if (query == null || query.isBlank()) {
                return null;
            }

            String search = "%" + query.toLowerCase() + "%";

            Join<TempleEntity, TempleI18nEntity> translation = root.join("translations");

            if (!Long.class.equals(cq.getResultType())) {
                cq.distinct(true);
            }

            return cb.or(
                    cb.like(cb.lower(translation.get("name")), search),
                    cb.like(cb.lower(translation.get("address")), search),
                    cb.like(cb.lower(translation.get("postalCode")), search)
            );
        };
    }
}
