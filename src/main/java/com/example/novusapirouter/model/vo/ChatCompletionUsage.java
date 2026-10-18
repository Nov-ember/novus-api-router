package com.example.novusapirouter.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor

public class ChatCompletionUsage {
    @JsonProperty("prompt_tokens")
    private final Integer promptTokens;

    @JsonProperty("completion_tokens")
    private final Integer completionTokens;

    @JsonProperty("total_tokens")
    private final Integer totalTokens;

    @JsonProperty("prompt_tokens_details")
    private final PromptTokensDetails promptTokensDetails;

    @Getter
    @AllArgsConstructor
    public static class PromptTokensDetails {
        @JsonProperty("cached_tokens")
        private final Integer cachedTokens;
    }
}

