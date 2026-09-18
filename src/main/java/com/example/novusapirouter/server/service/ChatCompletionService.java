package com.example.novusapirouter.server.service;

import com.example.novusapirouter.model.dto.OpenAiChatCompletionRequest;
import com.example.novusapirouter.model.vo.OpenAiChatCompletionResponse;

public interface ChatCompletionService {
    OpenAiChatCompletionResponse createAChatCompletion(String apiKey, OpenAiChatCompletionRequest request);
}
