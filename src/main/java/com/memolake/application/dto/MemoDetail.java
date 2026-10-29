package com.memolake.application.dto;

import com.memolake.domain.memo.Memo;
import java.time.Instant;

/**
 * A memo including its raw content.
 */
public record MemoDetail(String name, String content, Instant createdAt, Instant updatedAt, long version) {

    public static MemoDetail from(Memo memo) {
        return new MemoDetail(memo.getName(), memo.getContent(), memo.getCreatedAt(), memo.getUpdatedAt(),
                memo.getVersion());
    }
}
