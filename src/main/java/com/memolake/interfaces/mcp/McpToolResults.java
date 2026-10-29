package com.memolake.interfaces.mcp;

import com.memolake.common.exception.ErrorCode;
import com.memolake.common.exception.ErrorResponse;
import com.memolake.common.exception.MemoLakeException;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Converts service results and exceptions into MCP tool results.
 *
 * <p>Success: one text content item holding the result as JSON (same shape as the REST API).
 * Failure: {@code isError = true} with the error as JSON text and as {@code structuredContent}:
 * {@code {"code": "MEMO_NOT_FOUND", "message": "...", "timestamp": "..."}} - the codes are the same as in REST.
 */
@Component
class McpToolResults {

    private static final Logger log = LoggerFactory.getLogger(McpToolResults.class);

    private final JsonMapper jsonMapper;

    McpToolResults(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    CallToolResult run(Supplier<?> action) {
        try {
            return CallToolResult.builder()
                    .addTextContent(jsonMapper.writeValueAsString(action.get()))
                    .build();
        }
        catch (MemoLakeException e) {
            return error(e.getCode(), e.getMessage());
        }
        catch (ObjectOptimisticLockingFailureException e) {
            return error(ErrorCode.VERSION_CONFLICT, "The memo was modified concurrently. Re-read the memo and retry.");
        }
        catch (RuntimeException e) {
            log.error("Unhandled MCP tool error", e);
            return error(ErrorCode.INTERNAL_ERROR, "Internal server error");
        }
    }

    private CallToolResult error(ErrorCode code, String message) {
        ErrorResponse error = ErrorResponse.of(code, message);
        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("code", error.code());
        structured.put("message", error.message());
        structured.put("timestamp", error.timestamp().toString());
        return CallToolResult.builder()
                .isError(true)
                .addTextContent(jsonMapper.writeValueAsString(error))
                .structuredContent(structured)
                .build();
    }
}
