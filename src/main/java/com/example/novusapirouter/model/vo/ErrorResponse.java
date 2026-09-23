package com.example.novusapirouter.model.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ErrorResponse {

    private final ErrorDetail error;

    @Getter
    @AllArgsConstructor
    public static class ErrorDetail {
        private final String message;
        private final String type;
        private final String param;
        private final String code;
    }
}
