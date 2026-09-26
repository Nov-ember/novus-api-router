package com.example.novusapirouter.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor

public class ChatCompletionUsage {
    @JsonProperty("prompt_tokens")
    private final int promptTokens;

    @JsonProperty("completion_tokens")
    private final int completionTokens;

    @JsonProperty("total_tokens")
    private final int totalTokens;
}

