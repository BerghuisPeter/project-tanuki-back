package io.github.peterberghuis.goshuin.model;

import java.util.UUID;

public record CommentCountCursor(int commentCount, UUID id) implements GoshuinCursor {
}
