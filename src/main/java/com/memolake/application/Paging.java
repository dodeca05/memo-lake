package com.memolake.application;

import com.memolake.common.exception.MemoLakeException;
import org.springframework.data.domain.PageRequest;

/**
 * Paging rules shared by every list operation.
 */
public final class Paging {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 100;

    private Paging() {
    }

    public static PageRequest of(int page, int size) {
        if (page < 0) {
            throw MemoLakeException.invalidRequest("page must be 0 or greater");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw MemoLakeException.invalidRequest("size must be between 1 and " + MAX_SIZE);
        }
        return PageRequest.of(page, size);
    }
}
