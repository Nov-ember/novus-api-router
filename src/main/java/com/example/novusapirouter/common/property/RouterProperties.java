package com.example.novusapirouter.common.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "novus.router")
public class RouterProperties {
    private String accessKey;
    private Map<String, String> modelAliases = new HashMap<>();
}
