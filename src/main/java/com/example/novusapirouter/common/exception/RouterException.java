package com.example.novusapirouter.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class RouterException extends RuntimeException {
    private final HttpStatus status;
    private final String type;
    private final String param;
    private final String code;


    public RouterException(HttpStatus status, String message, String type, String code, String param) {
        super(message);
        this.status = status;
        this.type = type;
        this.param = param;
        this.code = code;
    }
}
