package com.memolake.application.dto;

import com.memolake.domain.memo.Memo;
import java.time.Instant;

/**
 * Result of a create / replace / append. The content is intentionally omitted so that
 * frequent appends do not send the whole memo back to the caller.
 */
public record MemoWriteResult(String name, Instant createdAt, Instant updatedAt, long version) {

    public static MemoWriteResult from(Memo memo) {
        return new MemoWriteResult(memo.getName(), memo.getCreatedAt(), memo.getUpdatedAt(), memo.getVersion());
    }
}
