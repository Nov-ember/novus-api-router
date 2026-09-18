package com.example.novusapirouter.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class OpenAiChatCompletionRequest {
    private String model;
    private List<Message> messages;
    private Boolean stream;

    @Getter
    @AllArgsConstructor
    public static class Message {
        private String role;
        private String content;
    }
}
