package com.example.novusapirouter.common.property;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "novus.router")
public class RouterProperties {
    private Long streamTimeOutMillis = 120_000L;
    private Map<String, Channel> channels = new HashMap<>();
    private Map<String, ModelMapping> modelAliases = new HashMap<>();
    private int maxActiveKeysPerUser = 200;

    @Getter
    @Setter
    public static class Channel {
        private String baseUrl;
        @ToString.Exclude
        private String apiKey;
    }

    @Getter
    @Setter
    public static class ModelMapping {
        private String channel;
        private String upstreamModel;
    }
}
