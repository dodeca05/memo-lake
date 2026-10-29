package com.memolake.common.exception;

import java.time.Instant;

/**
 * Error body returned by the REST API and, as JSON text, by failed MCP tool calls.
 */
public record ErrorResponse(String code, String message, Instant timestamp) {

    public static ErrorResponse of(ErrorCode code, String message) {
        return new ErrorResponse(code.name(), message, Instant.now());
    }
}
