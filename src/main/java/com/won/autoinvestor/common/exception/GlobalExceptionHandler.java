package com.won.autoinvestor.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public Map<String, Object> handleBusiness(BusinessException e) {
        logger.warn("Business error [{}] {}", e.getErrorCode(), e.getMessage());

        return Map.of(
                "success", false,
                "errorCode", e.getErrorCode(),
                "message", e.getMessage()
        );
    }

    @ExceptionHandler(Exception.class)
    public Map<String, Object> handleException(Exception e) {
        logger.error("Unexpected error", e);

        return Map.of(
                "success", false,
                "errorCode", "SYSTEM_ERROR",
                "message", "Internal server error"
        );
    }
}
