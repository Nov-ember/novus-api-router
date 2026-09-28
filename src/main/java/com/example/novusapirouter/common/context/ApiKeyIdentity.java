package com.example.novusapirouter.common.context;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApiKeyIdentity {
    private final Long userId;
    private final Long apiKeyId;
}
