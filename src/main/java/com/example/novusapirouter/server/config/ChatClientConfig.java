package com.example.novusapirouter.server.config;

import com.example.novusapirouter.common.property.RouterProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class ChatClientConfig {

    @Bean
    public Map<String, ChatClient> chatClients(RouterProperties properties) {
        Map<String, ChatClient> clients = new HashMap<>();

        properties.getChannels().forEach((channelId, channel) -> {
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .baseUrl(channel.getBaseUrl())
                    .apiKey(channel.getApiKey())
                    .maxRetries(0)
                    .build();

            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .options(options)
                    .build();

            clients.put(channelId, ChatClient.builder(chatModel).build());
        });

        return Map.copyOf(clients);
    }
}
