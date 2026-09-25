package com.example.novusapirouter.server.controller;

import com.example.novusapirouter.common.property.RouterProperties;
import com.example.novusapirouter.model.dto.ChatCompletionRequest;
import com.example.novusapirouter.model.vo.ChatCompletionChunkResponse;
import com.example.novusapirouter.server.service.ChatCompletionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/chat/completions")
public class ChatCompletionController {

    private final ChatCompletionService chatCompletionService;

    private final RouterProperties routerProperties;

    @PostMapping
    public ResponseEntity<?> chatCompletion(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                            @RequestBody ChatCompletionRequest request) {
        String apiKey = extractBearerToken(authorization);

        if (!Boolean.TRUE.equals(request.getStream())) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(chatCompletionService.chatCompletion(apiKey, request));
        } else {
            Flux<ChatCompletionChunkResponse> chunks = chatCompletionService.streamChatCompletion(apiKey, request);
            Flux<ServerSentEvent<Object>> events = chunks.timeout(Duration.ofMillis(routerProperties.getStreamTimeOutMillis()))
                    .map(chunk -> ServerSentEvent.builder()
                            .data(chunk)
                            .build())
                    .concatWithValues(
                            ServerSentEvent.builder()
                                    .data("[DONE]")
                                    .build()
                    );

            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(events);
        }
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return "";
        }
        return authorization.substring("Bearer ".length());
    }
}
