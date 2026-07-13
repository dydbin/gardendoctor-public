package com.project.farming.global.response;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.List;

public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        boolean hasNext,
        Long totalElements,
        Integer totalPages
) {
    public PageResponse {
        content = List.copyOf(content);
    }

    public static <T> PageResponse<T> from(Slice<T> slice) {
        return from(slice, slice.getContent());
    }

    public static <T> PageResponse<T> from(Slice<?> slice, List<T> content) {
        if (slice instanceof Page<?> pageResult) {
            return new PageResponse<>(
                    content,
                    pageResult.getNumber(),
                    pageResult.getSize(),
                    pageResult.hasNext(),
                    pageResult.getTotalElements(),
                    pageResult.getTotalPages()
            );
        }
        return new PageResponse<>(
                content,
                slice.getNumber(),
                slice.getSize(),
                slice.hasNext(),
                null,
                null
        );
    }

    public static <T> PageResponse<T> empty(Pageable pageable) {
        return new PageResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(), false, 0L, 0);
    }

    public static <T> PageResponse<T> of(
            List<T> content,
            int page,
            int size,
            boolean hasNext,
            long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(content, page, size, hasNext, totalElements, totalPages);
    }
}
