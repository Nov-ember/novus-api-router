package com.example.novusapirouter.server.service.impl;

import com.example.novusapirouter.common.context.ApiKeyIdentity;
import com.example.novusapirouter.common.exception.RouterException;
import com.example.novusapirouter.common.property.RouterProperties;
import com.example.novusapirouter.model.dto.ChatCompletionRequest;
import com.example.novusapirouter.model.vo.ChatCompletionChunkResponse;
import com.example.novusapirouter.model.vo.ChatCompletionResponse;
import com.example.novusapirouter.model.vo.ChatCompletionUsage;
import com.example.novusapirouter.server.service.ApiKeyService;
import com.example.novusapirouter.server.service.BillService;
import com.example.novusapirouter.server.service.ChatCompletionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatCompletionServiceImpl implements ChatCompletionService {

    private final Map<String, ChatClient> chatClients;
    private final RouterProperties routerProperties;
    private final ApiKeyService apiKeyService;
    private final BillService billService;
    private final TokenCountEstimator tokenCountEstimator = new JTokkitTokenCountEstimator();

    @Override
    public ChatCompletionResponse chatCompletion(String apiKey, ChatCompletionRequest request) {
        ApiKeyIdentity apiKeyIdentity = apiKeyService.checkApiKey(apiKey);
        checkRequest(request);

        String modelAlias = request.getModel();
        RouterProperties.ModelConfig modelConfig = resolveModel(modelAlias);
        ChatClient chatClient = chatClients.get(modelConfig.getChannel());
        String upstreamModel = modelConfig.getUpstreamModel();

        List<Message> messages = request.getMessages().stream()
                .map(this::toSpringAiMessage)
                .toList();

        Integer inputBudgetTokens = estimateInputTokens(messages);
        Integer maxOutputTokens = request.getMaxCompletionTokens() == null ?
                routerProperties.getDefaultMaxCompletionTokens()
                : request.getMaxCompletionTokens();

        Long billId = billService.reserve(apiKeyIdentity, modelAlias, inputBudgetTokens, maxOutputTokens);

        try {
            ChatResponse chatResponse = chatClient.prompt()
                    .messages(messages)
                    .options(ChatOptions.builder().model(upstreamModel).maxTokens(maxOutputTokens))
                    .call()
                    .chatResponse();

            ChatCompletionResponse result = toOpenAiResponse(modelAlias, chatResponse);

            ChatCompletionUsage usage = result.getUsage();
            billService.settle(billId, usage.getPromptTokens(), usage.getCompletionTokens());

            return result;
        } catch (Exception e) {
            try {
                billService.release(billId);
            } catch (Exception releaseError) {
                e.addSuppressed(releaseError);
                log.error("非流式调用释放预留失败，billId={}", billId, releaseError);
            }
            throw e;
        }
    }

    @Override
    public Flux<ChatCompletionChunkResponse> streamChatCompletion(String apiKey, ChatCompletionRequest request) {
        ApiKeyIdentity apiKeyIdentity = apiKeyService.checkApiKey(apiKey);
        checkRequest(request);

        String modelAlias = request.getModel();
        RouterProperties.ModelConfig modelConfig = resolveModel(modelAlias);
        ChatClient chatClient = chatClients.get(modelConfig.getChannel());
        String upstreamModel = modelConfig.getUpstreamModel();

        List<Message> messages = request.getMessages().stream()
                .map(this::toSpringAiMessage)
                .toList();

        Integer inputBudgetTokens = estimateInputTokens(messages);
        Integer maxOutputTokens = request.getMaxCompletionTokens() == null ?
                routerProperties.getDefaultMaxCompletionTokens()
                : request.getMaxCompletionTokens();


        return Flux.defer(() -> {
            Long billId = billService.reserve(apiKeyIdentity, modelAlias, inputBudgetTokens, maxOutputTokens);

            return Flux.defer(() -> {
                        Flux<ChatResponse> chatResponseFlux = chatClient.prompt()
                                .messages(messages)
                                .options(ChatOptions.builder().model(upstreamModel).maxTokens(maxOutputTokens))
                                .stream()
                                .chatResponse();

                        long created = Instant.now().getEpochSecond();

                        return chatResponseFlux
                                .doOnNext(chatResponse -> {
                                    Usage usage = chatResponse.getMetadata().getUsage();
                                    if (usage.getTotalTokens() > 0) {
                                        billService.settle(billId, usage.getPromptTokens(), usage.getCompletionTokens());
                                    }
                                })
                                .index()
                                .concatMap(tuple2 -> toOpenAiChunkResponse(
                                        modelAlias,
                                        tuple2.getT2(),
                                        created,
                                        tuple2.getT1() == 0
                                ));
                    })
                    .timeout(Duration.ofMillis(routerProperties.getStreamTimeOutMillis()))
                    .doOnError(error -> {
                        try {
                            billService.release(billId);
                        } catch (Exception releaseError) {
                            error.addSuppressed(releaseError);
                            log.error("流式调用释放预留失败，billId={}", billId, releaseError);
                        }
                    });
        });
    }

    private RouterProperties.ModelConfig resolveModel(String modelAlias) {
        RouterProperties.ModelConfig modelConfig = routerProperties.getModelAliases().get(modelAlias);
        if (modelConfig == null) {
            throw new RouterException(
                    HttpStatus.NOT_FOUND,
                    "模型不存在",
                    "invalid_request_error",
                    "model_not_found",
                    "model"
            );
        }
        return modelConfig;
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

        if (request.getMaxCompletionTokens() != null && request.getMaxCompletionTokens() <= 0) {
            throw new RouterException(
                    HttpStatus.BAD_REQUEST,
                    "输出 token 上限必须为正数",
                    "invalid_request_error",
                    "invalid_request",
                    "max_completion_tokens"
            );
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

    private String normalizeFinishReason(String finishReason) {
        return finishReason == null ? null : finishReason.toLowerCase(Locale.ROOT);
    }

    private Integer estimateInputTokens(List<Message> messages) {
        return messages.stream()
                .mapToInt(message -> tokenCountEstimator.estimate(message.getText()))
                .sum();
    }
}
