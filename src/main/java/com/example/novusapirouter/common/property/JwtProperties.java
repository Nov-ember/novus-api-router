package com.example.novusapirouter.common.property;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "novus.jwt")
public class JwtProperties {
    private String secret;
    private Long expireHours;
}
