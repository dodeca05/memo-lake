package com.memolake.common.exception;

/**
 * Error codes shared by the REST API, the MCP tools and the web UI.
 * {@link #httpStatus()} is the status used by the HTTP interfaces; MCP returns the code name in a tool error result.
 */
public enum ErrorCode {
    INVALID_REQUEST(400),
    UNAUTHORIZED(401),
    FORBIDDEN(403),
    TOPIC_NOT_FOUND(404),
    MEMO_NOT_FOUND(404),
    NOT_FOUND(404),
    METHOD_NOT_ALLOWED(405),
    TOPIC_ALREADY_EXISTS(409),
    MEMO_ALREADY_EXISTS(409),
    VERSION_CONFLICT(412),
    CONTENT_TOO_LARGE(413),
    UNSUPPORTED_MEDIA_TYPE(415),
    INTERNAL_ERROR(500);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }

    /** Best matching code for an HTTP status produced by the framework (routing, parsing, ...). */
    public static ErrorCode fromHttpStatus(int status) {
        return switch (status) {
            case 400 -> INVALID_REQUEST;
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 412 -> VERSION_CONFLICT;
            case 413 -> CONTENT_TOO_LARGE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            default -> status < 500 ? INVALID_REQUEST : INTERNAL_ERROR;
        };
    }
}
