package com.example.novusapirouter.server.service.impl;

import com.example.novusapirouter.common.exception.RouterException;
import com.example.novusapirouter.common.property.RouterProperties;
import com.example.novusapirouter.model.dto.OpenAiChatCompletionRequest;
import com.example.novusapirouter.model.vo.OpenAiChatCompletionResponse;
import com.example.novusapirouter.server.service.ChatCompletionService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ChatCompletionServiceImpl implements ChatCompletionService {

    private final ChatClient chatClient;
    private final RouterProperties routerProperties;

    @Override
    public OpenAiChatCompletionResponse createAChatCompletion(String apiKey, OpenAiChatCompletionRequest request) {
        checkApiKey(apiKey);
        checkRequest(request);

        String modelAlias = request.getModel();
        String upstreamModel = routerProperties.getModelAliases().get(modelAlias);

        checkModel(upstreamModel);

        List<Message> messages = request.getMessages().stream()
                .map(this::toSpringAiMessage)
                .toList();

        ChatResponse chatResponse = chatClient.prompt()
                .messages(messages)
                .options(ChatOptions.builder().model(upstreamModel))
                .call()
                .chatResponse();

        return toOpenAiResponse(upstreamModel, chatResponse);
    }

    private void checkModel(String model) {
        if (model == null) {
            throw new RouterException(
                    HttpStatus.NOT_FOUND,
                    "模型不存在",
                    "invalid_request_error",
                    "model_not_found",
                    "model"
            );
        }
        if (model.isBlank()) {
            throw new RouterException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "模型映射配置异常",
                    "server_error",
                    "internal_error",
                    null
            );
        }
    }

    private void checkRequest(OpenAiChatCompletionRequest request) {
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
        if (Boolean.TRUE.equals(request.getStream())) {
            throw new RouterException(
                    HttpStatus.BAD_REQUEST,
                    "当前版本不支持 stream 选项",
                    "invalid_request_error",
                    "unsupported_parameter",
                    "stream"
            );
        }

        for (int i = 0; i < request.getMessages().size(); i++) {
            OpenAiChatCompletionRequest.Message message = request.getMessages().get(i);
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

    private OpenAiChatCompletionResponse toOpenAiResponse(String requestedModel, ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null) {
            throw new RouterException(
                    HttpStatus.BAD_GATEWAY,
                    "上游未返回有效的消息结果",
                    "server_error",
                    "invalid_upstream_response",
                    null
            );
        }

        String id = chatResponse.getMetadata().getId();

        Generation generation = chatResponse.getResult();
        String content = generation.getOutput().getText();
        String finishReason = generation.getMetadata().getFinishReason();

        OpenAiChatCompletionResponse.Message message =
                new OpenAiChatCompletionResponse.Message("assistant", content);

        OpenAiChatCompletionResponse.Choice choice =
                new OpenAiChatCompletionResponse.Choice(0, message, finishReason);

        return new OpenAiChatCompletionResponse(
                id,
                "chat.completion",
                Instant.now().getEpochSecond(),
                requestedModel,
                List.of(choice)
        );
    }


    private Message toSpringAiMessage(OpenAiChatCompletionRequest.Message message) {
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
}
