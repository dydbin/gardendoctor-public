package com.project.farming.global.pagination;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

public final class CreatedAtIdCursorCodec {

    private static final int MAX_CURSOR_LENGTH = 256;
    private static final String DELIMITER = "|";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String INVALID_CURSOR_MESSAGE = "유효하지 않은 cursor입니다.";

    private CreatedAtIdCursorCodec() {
    }

    public static String encode(CreatedAtIdCursor cursor) {
        String payload = DATE_TIME_FORMATTER.format(cursor.createdAt()) + DELIMITER + cursor.id();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public static CreatedAtIdCursor decode(String encodedCursor) {
        if (encodedCursor == null || encodedCursor.isBlank() || encodedCursor.length() > MAX_CURSOR_LENGTH) {
            throw new IllegalArgumentException(INVALID_CURSOR_MESSAGE);
        }
        try {
            String payload = new String(
                    Base64.getUrlDecoder().decode(encodedCursor),
                    StandardCharsets.UTF_8
            );
            String[] values = payload.split("\\|", -1);
            if (values.length != 2) {
                throw new IllegalArgumentException(INVALID_CURSOR_MESSAGE);
            }
            return new CreatedAtIdCursor(
                    LocalDateTime.parse(values[0], DATE_TIME_FORMATTER),
                    Long.parseLong(values[1])
            );
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(INVALID_CURSOR_MESSAGE, ex);
        }
    }
}
