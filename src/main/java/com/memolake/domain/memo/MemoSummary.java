package com.memolake.domain.memo;

import java.time.Instant;

public record MemoSummary(String name, Instant updatedAt) {
}
