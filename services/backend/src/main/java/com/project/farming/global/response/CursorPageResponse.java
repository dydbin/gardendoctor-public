package com.project.farming.global.response;

import java.util.List;

public record CursorPageResponse<T>(
        List<T> content,
        int size,
        boolean hasNext,
        String nextCursor
) {
    public CursorPageResponse {
        content = List.copyOf(content);
        if (size < 1) {
            throw new IllegalArgumentException("size must be positive");
        }
        if (hasNext && (nextCursor == null || nextCursor.isBlank())) {
            throw new IllegalArgumentException("nextCursor is required when hasNext is true");
        }
        if (!hasNext && nextCursor != null) {
            throw new IllegalArgumentException("nextCursor must be null on the final page");
        }
    }
}
