package io.github.peterberghuis.goshuin.model;

import java.time.OffsetDateTime;

public record CommentCountCursor(int commentCount, OffsetDateTime createdAt) implements GoshuinCursor {
}
