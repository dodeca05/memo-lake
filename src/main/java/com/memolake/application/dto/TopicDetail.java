package com.memolake.application.dto;

import com.memolake.domain.memo.MemoSummary;

/**
 * A topic together with one page of its memos (names and timestamps only, no content).
 */
public record TopicDetail(String name, String description, PageResult<MemoSummary> memos) {
}
