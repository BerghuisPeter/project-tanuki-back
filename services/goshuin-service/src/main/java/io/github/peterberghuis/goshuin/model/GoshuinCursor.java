package io.github.peterberghuis.goshuin.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record GoshuinCursor(
        OffsetDateTime createdAt,
        UUID id
) {
}