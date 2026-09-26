package com.example.novusapirouter.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ChatCompletionChunkResponse {
    private final String id;
    private final String object;
    private final long created;
    private final String model;
    private final List<Choice> choices;
    private final ChatCompletionUsage usage;

    @Getter
    @AllArgsConstructor
    public static class Choice {
        private final int index;
        private final Delta delta;

        @JsonProperty("finish_reason")
        private final String finishReason;
    }

    @Getter
    @AllArgsConstructor
    // 值为 null 的字段不展示在 JSON 中
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Delta {
        private final String role;
        private final String content;
    }
}
