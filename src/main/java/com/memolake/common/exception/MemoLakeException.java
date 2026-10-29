package com.memolake.common.exception;

/**
 * Domain exception thrown by the application services. The message is safe to show to clients.
 */
public class MemoLakeException extends RuntimeException {

    private final ErrorCode code;

    public MemoLakeException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }

    public static MemoLakeException invalidRequest(String message) {
        return new MemoLakeException(ErrorCode.INVALID_REQUEST, message);
    }

    public static MemoLakeException topicNotFound(String topicName) {
        return new MemoLakeException(ErrorCode.TOPIC_NOT_FOUND, "Topic not found: '%s'".formatted(topicName));
    }

    public static MemoLakeException memoNotFound(String topicName, String memoName) {
        return new MemoLakeException(ErrorCode.MEMO_NOT_FOUND,
                "Memo not found: '%s' in topic '%s'".formatted(memoName, topicName));
    }

    public static MemoLakeException topicAlreadyExists(String topicName) {
        return new MemoLakeException(ErrorCode.TOPIC_ALREADY_EXISTS,
                "Topic already exists: '%s'".formatted(topicName));
    }

    public static MemoLakeException memoAlreadyExists(String topicName, String memoName) {
        return new MemoLakeException(ErrorCode.MEMO_ALREADY_EXISTS,
                "Memo already exists: '%s' in topic '%s'".formatted(memoName, topicName));
    }

    public static MemoLakeException versionConflict(long expectedVersion, long currentVersion) {
        return new MemoLakeException(ErrorCode.VERSION_CONFLICT,
                "Version conflict: expected version %d but current version is %d. Re-read the memo and retry."
                        .formatted(expectedVersion, currentVersion));
    }

    public static MemoLakeException contentTooLarge(long maxBytes) {
        return new MemoLakeException(ErrorCode.CONTENT_TOO_LARGE,
                "Memo content exceeds the maximum size of %d bytes (UTF-8)".formatted(maxBytes));
    }
}
