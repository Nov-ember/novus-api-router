package com.example.novusapirouter.server.controller;

import com.example.novusapirouter.common.result.Result;
import com.example.novusapirouter.model.dto.ApiKeyNameDTO;
import com.example.novusapirouter.model.vo.ApiKeyVO;
import com.example.novusapirouter.server.service.ApiKeyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {
    private final ApiKeyService apiKeyService;

    @PostMapping
    public Result<String> createApiKey(@Valid @RequestBody ApiKeyNameDTO apiKeyNameDTO) {
        String apiKey = apiKeyService.createApiKey(apiKeyNameDTO);
        return Result.success(apiKey);
    }

    @GetMapping
    public Result<List<ApiKeyVO>> listApiKeys() {
        List<ApiKeyVO> apiKeyVOList = apiKeyService.listApiKeys();
        return Result.success(apiKeyVOList);
    }

    @PatchMapping("/{id}")
    public Result<Void> renameApiKey(@PathVariable Long id, @Valid @RequestBody ApiKeyNameDTO apiKeyNameDTO) {
        apiKeyService.renameApiKey(id, apiKeyNameDTO);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteApiKey(@PathVariable Long id) {
        apiKeyService.revokeApiKey(id);
        return Result.success();
    }
}
