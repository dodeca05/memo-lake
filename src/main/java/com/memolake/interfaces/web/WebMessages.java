package com.memolake.interfaces.web;

import com.memolake.common.exception.MemoLakeException;

/**
 * User-facing (Korean) messages for domain errors shown in the web UI.
 */
final class WebMessages {

    private WebMessages() {
    }

    static String of(MemoLakeException e) {
        return switch (e.getCode()) {
            case TOPIC_NOT_FOUND -> "토픽을 찾을 수 없습니다.";
            case MEMO_NOT_FOUND -> "메모를 찾을 수 없습니다.";
            case TOPIC_ALREADY_EXISTS -> "같은 이름의 토픽이 이미 있습니다.";
            case MEMO_ALREADY_EXISTS -> "같은 이름의 메모가 이미 있습니다.";
            case VERSION_CONFLICT -> "편집하는 동안 다른 사용자나 에이전트가 메모를 먼저 수정했습니다.";
            case CONTENT_TOO_LARGE -> "메모가 허용된 최대 크기를 넘었습니다. (" + e.getMessage() + ")";
            case INVALID_REQUEST -> "잘못된 요청입니다. " + e.getMessage();
            default -> "요청을 처리하지 못했습니다.";
        };
    }
}
