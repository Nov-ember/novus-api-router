package com.example.novusapirouter.server.service;

import com.example.novusapirouter.model.dto.ChatCompletionRequest;
import com.example.novusapirouter.model.vo.ChatCompletionChunkResponse;
import com.example.novusapirouter.model.vo.ChatCompletionResponse;
import reactor.core.publisher.Flux;

public interface ChatCompletionService {
    ChatCompletionResponse chatCompletion(String apiKey, ChatCompletionRequest request);

    Flux<ChatCompletionChunkResponse> streamChatCompletion(String apiKey, ChatCompletionRequest request);
}
