package com.memolake.application.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Page of results with the paging metadata returned by every list operation.
 */
public record PageResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResult<T> from(Page<T> page) {
        return new PageResult<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    public boolean hasPrevious() {
        return page > 0;
    }

    public boolean hasNext() {
        return page + 1 < totalPages;
    }
}
