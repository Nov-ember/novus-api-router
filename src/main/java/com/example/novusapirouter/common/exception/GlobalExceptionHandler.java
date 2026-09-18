package com.example.novusapirouter.common.exception;

import com.example.novusapirouter.model.vo.OpenAiErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RouterException.class)
    public ResponseEntity<OpenAiErrorResponse> handleRouterException(RouterException e) {
        OpenAiErrorResponse.ErrorDetail errorDetail = new OpenAiErrorResponse.ErrorDetail(
                e.getMessage(),
                e.getType(),
                e.getParam(),
                e.getCode()
        );

        OpenAiErrorResponse errorResponse = new OpenAiErrorResponse(errorDetail);

        return new ResponseEntity<>(errorResponse, e.getStatus());
    }
}
