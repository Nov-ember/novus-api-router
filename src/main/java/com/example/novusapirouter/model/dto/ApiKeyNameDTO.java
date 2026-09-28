package com.example.novusapirouter.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class ApiKeyNameDTO {

    @NotBlank(message = "Key 名称不能为空")
    @Size(max = 50, message = "Key 名称不能超过 50 个字符")
    private String name;

    public void setName(String name) {
        this.name = name == null ? null : name.strip();
    }
}
