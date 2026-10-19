package com.example.novusapirouter.common.property;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "novus.router")
public class RouterProperties {
    private Long streamTimeOutMillis = 120_000L;
    private Integer maxActiveKeysPerUser = 200;
    private Integer defaultMaxCompletionTokens = 4096;
    private Integer prefixLengthChars = 1024;
    private Long sessionAffinityTtlSeconds = 3600L;
    private Long prefixRoutingTtlSeconds = 300L;
    private Map<String, ModelConfig> modelAliases = new HashMap<>();
    private Map<String, Channel> channels = new HashMap<>();

    @Getter
    @Setter
    public static class Channel {
        private String platform;
        private String baseUrl;
        @ToString.Exclude
        private String apiKey;
    }

    @Getter
    @Setter
    public static class ModelConfig {
        private List<Route> routes = new ArrayList<>();
        private BigDecimal inputPricePerMillion;
        private BigDecimal cachedInputPricePerMillion;
        private BigDecimal outputPricePerMillion;
    }

    @Getter
    @Setter
    public static class Route {
        private Long id;
        private String channel;
        private String upstreamModel;
    }
}
