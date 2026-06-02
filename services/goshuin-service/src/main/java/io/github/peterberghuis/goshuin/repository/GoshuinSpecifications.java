package io.github.peterberghuis.goshuin.repository;

import io.github.peterberghuis.goshuin.dto.AffiliationType;
import io.github.peterberghuis.goshuin.dto.GoshuinFormat;
import io.github.peterberghuis.goshuin.entity.GoshuinEntity;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;

public class GoshuinSpecifications {

    public static Specification<GoshuinEntity> withFormat(GoshuinFormat format) {
        return (root, query, cb) -> format == null ? null : cb.equal(root.get("format"), format.toString());
    }

    public static Specification<GoshuinEntity> withPages(List<Integer> pages) {
        return (root, query, cb) -> (pages == null || pages.isEmpty()) ? null : root.get("pages").in(pages);
    }

    public static Specification<GoshuinEntity> withStartDate(LocalDate startDate) {
        return (root, query, cb) -> startDate == null ? null : cb.greaterThanOrEqualTo(root.get("startDate"), startDate);
    }

    public static Specification<GoshuinEntity> withEndDate(LocalDate endDate) {
        return (root, query, cb) -> endDate == null ? null : cb.lessThanOrEqualTo(root.get("endDate"), endDate);
    }

    public static Specification<GoshuinEntity> withAffiliation(AffiliationType affiliation) {
        return (root, query, cb) -> affiliation == null ? null : cb.equal(root.get("temple").get("affiliationType"), affiliation.toString());
    }
}
