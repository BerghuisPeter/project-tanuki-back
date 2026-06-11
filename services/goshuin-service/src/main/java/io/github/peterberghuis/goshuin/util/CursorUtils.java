package io.github.peterberghuis.goshuin.util;

import io.github.peterberghuis.goshuin.model.CommentCountCursor;
import io.github.peterberghuis.goshuin.model.CreatedAtCursor;
import io.github.peterberghuis.goshuin.model.GoshuinCursor;
import io.github.peterberghuis.goshuin.model.ProximityCursor;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

public final class CursorUtils {

    private CursorUtils() {
    }

    public static String encode(GoshuinCursor cursor) {
        String raw = switch (cursor) {
            case CreatedAtCursor c -> "CREATED_AT|" + c.createdAt() + "|" + c.id();
            case CommentCountCursor c -> "COMMENT_COUNT|" + c.commentCount() + "|" + c.createdAt();
            case ProximityCursor c -> "PROXIMITY|" + c.offset();
        };

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static GoshuinCursor decode(String token) {
        String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
        String[] parts = raw.split("\\|");

        return switch (parts[0]) {
            case "CREATED_AT" -> new CreatedAtCursor(OffsetDateTime.parse(parts[1]), UUID.fromString(parts[2]));
            case "COMMENT_COUNT" -> new CommentCountCursor(Integer.parseInt(parts[1]), OffsetDateTime.parse(parts[2]));
            case "PROXIMITY" -> new ProximityCursor(Integer.parseInt(parts[1]));
            default -> throw new IllegalArgumentException("Unknown cursor type: " + parts[0]);
        };
    }
}