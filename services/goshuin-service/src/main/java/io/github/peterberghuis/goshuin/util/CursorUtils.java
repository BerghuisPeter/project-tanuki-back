package io.github.peterberghuis.goshuin.util;

import io.github.peterberghuis.goshuin.model.GoshuinCursor;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

public final class CursorUtils {

    private CursorUtils() {
    }

    public static String encode(GoshuinCursor cursor) {

        String raw =
                cursor.createdAt().toString()
                        + "|"
                        + cursor.id();

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static GoshuinCursor decode(String token) {

        String decoded = new String(
                Base64.getUrlDecoder().decode(token),
                StandardCharsets.UTF_8
        );

        String[] parts = decoded.split("\\|");

        return new GoshuinCursor(
                OffsetDateTime.parse(parts[0]),
                UUID.fromString(parts[1])
        );
    }
}