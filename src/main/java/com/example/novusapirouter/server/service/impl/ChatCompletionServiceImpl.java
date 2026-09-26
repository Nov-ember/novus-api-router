package com.example.novusapirouter.server.service.impl;

import com.example.novusapirouter.common.exception.RouterException;
import com.example.novusapirouter.common.property.RouterProperties;
import com.example.novusapirouter.model.dto.ChatCompletionRequest;
import com.example.novusapirouter.model.vo.ChatCompletionChunkResponse;
import com.example.novusapirouter.model.vo.ChatCompletionResponse;
import com.example.novusapirouter.model.vo.ChatCompletionUsage;
import com.example.novusapirouter.server.service.ChatCompletionService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ChatCompletionServiceImpl implements ChatCompletionService {

    private final Map<String, ChatClient> chatClients;
    private final RouterProperties routerProperties;

    @Override
    public ChatCompletionResponse chatCompletion(String apiKey, ChatCompletionRequest request) {
        checkApiKey(apiKey);
        checkRequest(request);

        String modelAlias = request.getModel();
        RouterProperties.ModelMapping mapping = resolveModel(modelAlias);
        ChatClient chatClient = chatClients.get(mapping.getChannel());
        String upstreamModel = mapping.getUpstreamModel();

        List<Message> messages = request.getMessages().stream()
                .map(this::toSpringAiMessage)
                .toList();

        ChatResponse chatResponse = chatClient.prompt()
                .messages(messages)
                .options(ChatOptions.builder().model(upstreamModel))
                .call()
                .chatResponse();

        return toOpenAiResponse(modelAlias, chatResponse);
    }

    @Override
    public Flux<ChatCompletionChunkResponse> streamChatCompletion(String apiKey, ChatCompletionRequest request) {
        checkApiKey(apiKey);
        checkRequest(request);

        String modelAlias = request.getModel();
        RouterProperties.ModelMapping mapping = resolveModel(modelAlias);
        ChatClient chatClient = chatClients.get(mapping.getChannel());
        String upstreamModel = mapping.getUpstreamModel();

        List<Message> messages = request.getMessages().stream()
                .map(this::toSpringAiMessage)
                .toList();

        Flux<ChatResponse> chatResponseFlux = chatClient.prompt()
                .messages(messages)
                .options(ChatOptions.builder().model(upstreamModel))
                .stream()
                .chatResponse();

        long created = Instant.now().getEpochSecond();

        return chatResponseFlux
                .index()
                .concatMap(tuple2 -> toOpenAiChunkResponse(
                        modelAlias,
                        tuple2.getT2(),
                        created,
                        tuple2.getT1() == 0
                ));
    }

    private RouterProperties.ModelMapping resolveModel(String modelAlias) {
        RouterProperties.ModelMapping mapping = routerProperties.getModelAliases().get(modelAlias);
        if (mapping == null) {
            throw new RouterException(
                    HttpStatus.NOT_FOUND,
                    "模型不存在",
                    "invalid_request_error",
                    "model_not_found",
                    "model"
            );
        }
        return mapping;
    }

    private void checkRequest(ChatCompletionRequest request) {
        if (request == null) {
            throw new RouterException(
                    HttpStatus.BAD_REQUEST,
                    "request 不能为空",
                    "invalid_request_error",
                    "invalid_request",
                    null
            );
        }
        if (request.getModel() == null || request.getModel().isBlank()) {
            throw new RouterException(
                    HttpStatus.BAD_REQUEST,
                    "model 不能为空",
                    "invalid_request_error",
                    "missing_required_parameter",
                    "model"
            );
        }
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new RouterException(
                    HttpStatus.BAD_REQUEST,
                    "messages 不能为空",
                    "invalid_request_error",
                    "missing_required_parameter",
                    "messages"
            );
        }
        for (int i = 0; i < request.getMessages().size(); i++) {
            ChatCompletionRequest.Message message = request.getMessages().get(i);
            String param = "messages[" + i + "]";
            if (message == null) {
                throw new RouterException(
                        HttpStatus.BAD_REQUEST,
                        "message 不能为空",
                        "invalid_request_error",
                        "invalid_request",
                        param
                );
            }
            if (message.getRole() == null || message.getRole().isBlank()) {
                throw new RouterException(
                        HttpStatus.BAD_REQUEST,
                        "role 不能为空",
                        "invalid_request_error",
                        "missing_required_parameter",
                        param + ".role"
                );
            }
            if (message.getContent() == null) {
                throw new RouterException(
                        HttpStatus.BAD_REQUEST,
                        "content 不能为空",
                        "invalid_request_error",
                        "missing_required_parameter",
                        param + ".content"
                );
            }
        }
    }

    private ChatCompletionResponse toOpenAiResponse(String modelAlias, ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null) {
            throw new RouterException(
                    HttpStatus.BAD_GATEWAY,
                    "上游未返回有效的消息结果",
                    "server_error",
                    "invalid_upstream_response",
                    null
            );
        }

        ChatResponseMetadata metadata = chatResponse.getMetadata();

        String id = metadata.getId();

        Generation generation = chatResponse.getResult();
        String content = generation.getOutput().getText();
        String finishReason = normalizeFinishReason(generation.getMetadata().getFinishReason());

        ChatCompletionResponse.Message message =
                new ChatCompletionResponse.Message("assistant", content);

        ChatCompletionResponse.Choice choice =
                new ChatCompletionResponse.Choice(0, message, finishReason);

        Usage upstreamUsage = metadata.getUsage();
        ChatCompletionUsage usage = new ChatCompletionUsage(
                upstreamUsage.getPromptTokens(),
                upstreamUsage.getCompletionTokens(),
                upstreamUsage.getTotalTokens()
        );

        return new ChatCompletionResponse(
                id,
                "chat.completion",
                Instant.now().getEpochSecond(),
                modelAlias,
                List.of(choice),
                usage
        );
    }

    private Flux<ChatCompletionChunkResponse> toOpenAiChunkResponse(String modelAlias, ChatResponse chatResponse, long created, boolean first) {

        ChatResponseMetadata metadata = chatResponse.getMetadata();

        String id = metadata.getId();
        List<ChatCompletionChunkResponse> chunks = new ArrayList<>(2);

        Generation generation = chatResponse.getResult();

        if (generation != null) {
            AssistantMessage output = generation.getOutput();

            String content = output.getText();
            String finishReason = normalizeFinishReason(generation.getMetadata().getFinishReason());
            String role = first ? output.getMessageType().getValue() : null;

            ChatCompletionChunkResponse.Delta delta =
                    new ChatCompletionChunkResponse.Delta(role, content);

            ChatCompletionChunkResponse.Choice choice =
                    new ChatCompletionChunkResponse.Choice(0, delta, finishReason);

            chunks.add(new ChatCompletionChunkResponse(
                    id,
                    "chat.completion.chunk",
                    created,
                    modelAlias,
                    List.of(choice),
                    null
            ));
        }


        Usage upstreamUsage = metadata.getUsage();
        if (upstreamUsage.getTotalTokens() > 0) {
            ChatCompletionUsage usage = new ChatCompletionUsage(
                    upstreamUsage.getPromptTokens(),
                    upstreamUsage.getCompletionTokens(),
                    upstreamUsage.getTotalTokens()
            );


            chunks.add(new ChatCompletionChunkResponse(
                    id,
                    "chat.completion.chunk",
                    created,
                    modelAlias,
                    List.of(),
                    usage
            ));
        }

        return Flux.fromIterable(chunks);
    }

    private Message toSpringAiMessage(ChatCompletionRequest.Message message) {
        return switch (message.getRole()) {
            case "system" -> new SystemMessage(message.getContent());
            case "user" -> new UserMessage(message.getContent());
            case "assistant" -> new AssistantMessage(message.getContent());
            default -> throw new RouterException(
                    HttpStatus.BAD_REQUEST,
                    "消息类型不支持",
                    "invalid_request_error",
                    "unsupported_parameter",
                    "messages"
            );
        };
    }

    private void checkApiKey(String apiKey) {
        String accessKey = routerProperties.getAccessKey();
        if (accessKey == null || accessKey.isBlank()) {
            throw new RouterException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "服务配置错误",
                    "server_error",
                    "internal_error",
                    null
            );
        }
        if (apiKey == null || apiKey.isBlank() || !Objects.equals(apiKey, accessKey)) {
            throw new RouterException(
                    HttpStatus.UNAUTHORIZED,
                    "API Key 无效",
                    "invalid_request_error",
                    "invalid_api_key",
                    null
            );
        }
    }

    // finish_reason 字符串全大写转全小写
    private String normalizeFinishReason(String finishReason) {
        return finishReason == null ? null : finishReason.toLowerCase(Locale.ROOT);
    }
}
