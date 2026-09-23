package com.example.novusapirouter.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ChatCompletionRequest {
    private final String model;
    private final List<Message> messages;
    private final Boolean stream;

    @Getter
    @AllArgsConstructor
    public static class Message {
        private final String role;
        private final String content;
    }
}
