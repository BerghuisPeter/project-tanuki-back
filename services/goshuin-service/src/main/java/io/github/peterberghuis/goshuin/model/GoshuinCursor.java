package io.github.peterberghuis.goshuin.model;

public sealed interface GoshuinCursor permits CreatedAtCursor, CommentCountCursor, ProximityCursor {
}