package io.github.peterberghuis.goshuin.repository;

import io.github.peterberghuis.goshuin.dto.AffiliationType;
import io.github.peterberghuis.goshuin.dto.GoshuinFormat;
import io.github.peterberghuis.goshuin.entity.GoshuinEntity;
import jakarta.persistence.criteria.Join;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class GoshuinSpecifications {

    public static Specification<GoshuinEntity> withFormat(GoshuinFormat format) {
        return (root, _, cb) -> format == null ? null : cb.equal(root.get("format"), format.toString());
    }

    public static Specification<GoshuinEntity> withPages(List<Integer> pages) {
        return (root, _, cb) -> (pages == null || pages.isEmpty()) ? null : root.get("pages").in(pages);
    }

    public static Specification<GoshuinEntity> withStartDate(LocalDate startDate) {
        return (root, _, cb) -> startDate == null ? null : cb.greaterThanOrEqualTo(root.get("startDate"), startDate);
    }

    public static Specification<GoshuinEntity> withEndDate(LocalDate endDate) {
        return (root, _, cb) -> endDate == null ? null : cb.lessThanOrEqualTo(root.get("endDate"), endDate);
    }

    public static Specification<GoshuinEntity> withAffiliation(AffiliationType affiliation) {
        return (root, _, cb) -> affiliation == null ? null : cb.equal(root.get("temple").get("affiliationType"), affiliation.toString());
    }

    public static Specification<GoshuinEntity> withLabel(String label) {
        return (root, _, cb) -> {
            if (label == null || label.isBlank()) {
                return null;
            }
            return cb.like(cb.lower(root.join("translations").get("label")), "%" + label.toLowerCase() + "%");
        };
    }

    public static Specification<GoshuinEntity> withTempleName(String templeName) {
        return (root, _, cb) -> {
            if (templeName == null || templeName.isBlank()) {
                return null;
            }
            return cb.like(cb.lower(root.join("temple").join("translations").get("name")), "%" + templeName.toLowerCase() + "%");
        };
    }

    public static Specification<GoshuinEntity> withTempleTranslationSearch(String query) {
        return (root, cq, cb) -> {
            if (query == null || query.isBlank()) {
                return null;
            }

            String search = "%" + query.toLowerCase() + "%";

            Join<?, ?> translation = root
                    .join("temple")
                    .join("translations");

            return cb.or(
                    cb.like(cb.lower(translation.get("name")), search),
                    cb.like(cb.lower(translation.get("address")), search),
                    cb.like(cb.lower(translation.get("description")), search),
                    cb.like(cb.lower(translation.get("prefecture")), search),
                    cb.like(cb.lower(translation.get("postalCode")), search),
                    cb.like(cb.lower(translation.get("city")), search),
                    cb.like(cb.lower(translation.get("region")), search)
            );
        };
    }

    public static final Sort CREATED_AT_SORT =
            Sort.by(
                    Sort.Order.desc("createdAt"),
                    Sort.Order.desc("id")
            );

    public static Specification<GoshuinEntity> afterCreatedAtCursor(
            OffsetDateTime createdAt,
            UUID id
    ) {
        return (root, query, cb) ->
                cb.or(
                        cb.lessThan(
                                root.get("createdAt"),
                                createdAt
                        ),
                        cb.and(
                                cb.equal(
                                        root.get("createdAt"),
                                        createdAt
                                ),
                                cb.lessThan(
                                        root.get("id"),
                                        id
                                )
                        )
                );
    }

    public static final Sort COMMENT_COUNT_SORT =
            Sort.by(Sort.Direction.DESC, "commentCount");
}
