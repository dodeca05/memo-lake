package com.memolake.common.web;

import com.memolake.common.exception.ErrorCode;
import com.memolake.common.exception.ErrorResponse;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.ModelAndView;

/**
 * Handles errors raised before any controller runs (unknown path, unsupported method or content type,
 * malformed request rejected by the server). API clients get the usual {@link ErrorResponse} JSON,
 * browsers get the HTML error page.
 */
@Controller
@RequestMapping("${server.error.path:/error}")
public class AppErrorController implements ErrorController {

    private final ErrorAttributes errorAttributes;

    public AppErrorController(ErrorAttributes errorAttributes) {
        this.errorAttributes = errorAttributes;
    }

    @RequestMapping(produces = MediaType.TEXT_HTML_VALUE)
    public ModelAndView errorPage(HttpServletRequest request) {
        HttpStatus status = status(request);
        String message = switch (status.value()) {
            case 404 -> "페이지를 찾을 수 없습니다.";
            case 405, 415, 400 -> "잘못된 요청입니다.";
            default -> status.is5xxServerError() ? "서버에서 오류가 발생했습니다." : status.getReasonPhrase();
        };
        ModelAndView view = new ModelAndView("error",
                Map.of("status", status.value(), "error", status.getReasonPhrase(), "message", message));
        view.setStatus(status);
        return view;
    }

    @RequestMapping
    public ResponseEntity<ErrorResponse> error(HttpServletRequest request) {
        HttpStatus status = status(request);
        ErrorCode code = ErrorCode.fromHttpStatus(status.value());
        String message = status.getReasonPhrase();
        Throwable error = errorAttributes.getError(new ServletWebRequest(request));
        if (!status.is5xxServerError() && error instanceof org.springframework.web.ErrorResponse errorResponse
                && errorResponse.getBody().getDetail() != null) {
            message = errorResponse.getBody().getDetail();
        }
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of(code, message));
    }

    private static HttpStatus status(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus status = code instanceof Integer value ? HttpStatus.resolve(value) : null;
        return status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
