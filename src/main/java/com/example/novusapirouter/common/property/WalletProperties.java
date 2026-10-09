package com.example.novusapirouter.common.property;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "novus.wallet")
public class WalletProperties {
    private Set<Long> adminUserIds = new HashSet<>();
}
