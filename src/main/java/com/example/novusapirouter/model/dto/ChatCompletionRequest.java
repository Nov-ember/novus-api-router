package com.example.novusapirouter.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ChatCompletionRequest {
    private final String model;
    private final List<Message> messages;
    private final Boolean stream;
    @JsonProperty("max_completion_tokens")
    private final Integer maxCompletionTokens;

    @Getter
    @AllArgsConstructor
    public static class Message {
        private final String role;
        private final String content;
    }
}
