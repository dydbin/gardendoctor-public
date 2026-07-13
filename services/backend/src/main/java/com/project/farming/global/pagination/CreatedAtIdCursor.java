package com.project.farming.global.pagination;

import java.time.LocalDateTime;
import java.util.Objects;

public record CreatedAtIdCursor(LocalDateTime createdAt, Long id) {

    public CreatedAtIdCursor {
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
    }
}
