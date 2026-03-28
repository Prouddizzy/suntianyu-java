package com.suntianyu.stm32smartdisinfectorjava.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Locale;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("Business Exception: code={}, msg={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Object handleException(Exception e, HttpServletRequest request, HttpServletResponse response) {
        if (isSseRequest(request, response) && isIgnorableSseException(e)) {
            log.debug("SSE request closed by client: url={}", request.getRequestURI());
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }

        log.error("System Error: url={}", request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(500, "Internal Server Error: " + e.getMessage()));
    }

    private boolean isSseRequest(HttpServletRequest request, HttpServletResponse response) {
        String requestUri = request.getRequestURI();
        if (hasText(requestUri) && requestUri.contains("/stream")) {
            return true;
        }

        String accept = request.getHeader("Accept");
        if (hasText(accept) && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            return true;
        }

        String contentType = response.getContentType();
        return hasText(contentType) && contentType.contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    private boolean isIgnorableSseException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String className = current.getClass().getName();
            if (className.endsWith("AsyncRequestNotUsableException")
                    || className.contains("ClientAbortException")
                    || current instanceof HttpMessageNotWritableException) {
                return true;
            }

            if (hasDisconnectMessage(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean hasDisconnectMessage(String message) {
        if (!hasText(message)) {
            return false;
        }

        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("broken pipe")
                || normalized.contains("connection reset by peer")
                || normalized.contains("forcibly closed by the remote host")
                || normalized.contains("established connection was aborted");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

