package com.memolake.interfaces.web;

import com.memolake.common.exception.MemoLakeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;

/**
 * Renders errors from the web pages as an HTML error page.
 */
@ControllerAdvice(assignableTypes = WebController.class)
public class WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(WebExceptionHandler.class);

    @ExceptionHandler(MemoLakeException.class)
    ModelAndView handleDomain(MemoLakeException e) {
        return errorPage(HttpStatus.valueOf(e.getCode().httpStatus()), WebMessages.of(e));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    ModelAndView handleBadRequest(Exception e) {
        return errorPage(HttpStatus.BAD_REQUEST, "잘못된 요청입니다.");
    }

    @ExceptionHandler(Exception.class)
    ModelAndView handleUnexpected(Exception e) {
        log.error("Unhandled web error", e);
        return errorPage(HttpStatus.INTERNAL_SERVER_ERROR, "서버에서 오류가 발생했습니다.");
    }

    private static ModelAndView errorPage(HttpStatus status, String message) {
        ModelAndView view = new ModelAndView("error");
        view.setStatus(status);
        view.addObject("status", status.value());
        view.addObject("error", status.getReasonPhrase());
        view.addObject("message", message);
        return view;
    }
}
