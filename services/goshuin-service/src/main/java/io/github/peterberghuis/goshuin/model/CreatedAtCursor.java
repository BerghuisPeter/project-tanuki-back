package io.github.peterberghuis.goshuin.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreatedAtCursor(OffsetDateTime createdAt, UUID id) implements GoshuinCursor {
}
