package com.example.novusapirouter.model.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ApiKeyVO {
        private final Long id;
        private final String name;
        private final String maskedKey;
        private final Integer status;
        private final LocalDateTime createTime;
        private final LocalDateTime updateTime;
}
