package com.example.novusapirouter.server.controller;

import com.example.novusapirouter.model.dto.OpenAiChatCompletionRequest;
import com.example.novusapirouter.model.vo.OpenAiChatCompletionResponse;
import com.example.novusapirouter.server.service.ChatCompletionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chat/completions")
public class ChatCompletionController {

    private final ChatCompletionService chatCompletionService;

    @PostMapping
    public OpenAiChatCompletionResponse createAChatCompletion(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                                              @RequestBody OpenAiChatCompletionRequest request) {
        return chatCompletionService.createAChatCompletion(
                extractBearerToken(authorization),
                request
        );
    }


    private String extractBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return "";
        }
        return authorization.substring("Bearer ".length());
    }
}
