package com.example.novusapirouter.common.exception;

import com.example.novusapirouter.model.vo.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RouterException.class)
    public ResponseEntity<ErrorResponse> handleRouterException(RouterException e) {
        ErrorResponse.ErrorDetail errorDetail = new ErrorResponse.ErrorDetail(
                e.getMessage(),
                e.getType(),
                e.getParam(),
                e.getCode()
        );

        ErrorResponse errorResponse = new ErrorResponse(errorDetail);

        return new ResponseEntity<>(errorResponse, e.getStatus());
    }
}
