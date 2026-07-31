package com.won.autoinvestor.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(NoResourceFoundException.class)
    public Map<String, Object> handleNoResource(NoResourceFoundException e) {
        logger.debug("No static resource: {}", e.getResourcePath());
        return Map.of(
                "success", false,
                "errorCode", "NOT_FOUND",
                "message", "Resource not found"
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
