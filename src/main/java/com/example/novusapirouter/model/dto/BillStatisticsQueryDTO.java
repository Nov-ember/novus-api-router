package com.example.novusapirouter.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class BillStatisticsQueryDTO {
    @NotNull(message = "startTime 不能为空")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;

    @NotNull(message = "endTime 不能为空")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;

    @Positive(message = "apiKeyId 必须为正")
    private Long apiKeyId;

    private String modelAlias;
}
