package com.memolake.domain.topic;

import java.time.Instant;

/**
 * Topic list item. {@code lastMemoModifiedAt} is the newest memo {@code updatedAt}, or null when the topic has no memos.
 */
public record TopicSummary(String name, String description, Instant lastMemoModifiedAt) {
}
