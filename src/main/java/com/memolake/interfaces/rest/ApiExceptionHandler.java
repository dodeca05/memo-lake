package com.memolake.interfaces.rest;

import com.memolake.common.exception.ErrorCode;
import com.memolake.common.exception.ErrorResponse;
import com.memolake.common.exception.MemoLakeException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error raised by the REST controllers into {@link ErrorResponse} JSON.
 */
@RestControllerAdvice(basePackageClasses = ApiExceptionHandler.class)
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MemoLakeException.class)
    ResponseEntity<ErrorResponse> handleDomain(MemoLakeException e) {
        return error(e.getCode(), e.getMessage());
    }

    /** Safety net: Hibernate's version check failed (normally prevented by the row lock). */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
        return error(ErrorCode.VERSION_CONFLICT, "The memo was modified concurrently. Re-read the memo and retry.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unhandled API error", e);
        return error(ErrorCode.INTERNAL_ERROR, "Internal server error");
    }

    /** Standard Spring MVC errors: 400 / 404 / 405 / 415 ... */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ErrorCode code = ErrorCode.fromHttpStatus(statusCode.value());
        String message;
        if (statusCode.is5xxServerError()) {
            log.error("API error", ex);
            message = "Internal server error";
        }
        else if (ex instanceof MethodArgumentNotValidException invalid) {
            message = invalid.getBindingResult().getFieldErrors().stream()
                    .map(error -> error.getField() + " " + error.getDefaultMessage())
                    .collect(Collectors.joining("; "));
        }
        else if (body instanceof ProblemDetail problem && problem.getDetail() != null) {
            message = problem.getDetail();
        }
        else {
            message = ex.getMessage();
        }
        return ResponseEntity.status(statusCode).headers(headers).body(ErrorResponse.of(code, message));
    }

    private static ResponseEntity<ErrorResponse> error(ErrorCode code, String message) {
        return ResponseEntity.status(code.httpStatus()).body(ErrorResponse.of(code, message));
    }
}
