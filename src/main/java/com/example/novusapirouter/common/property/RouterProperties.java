package com.example.novusapirouter.common.property;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "novus.router")
public class RouterProperties {
    private String accessKey;
    private Long streamTimeOutMillis = 120_000L;
    private Map<String, Channel> channels = new HashMap<>();
    private Map<String, ModelMapping> modelAliases = new HashMap<>();

    @Data
    public static class Channel {
        private String baseUrl;
        @ToString.Exclude
        private String apiKey;
    }

    @Data
    public static class ModelMapping {
        private String channel;
        private String upstreamModel;
    }
}
