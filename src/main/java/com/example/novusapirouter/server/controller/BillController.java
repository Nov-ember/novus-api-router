package com.example.novusapirouter.server.controller;

import com.example.novusapirouter.common.result.Result;
import com.example.novusapirouter.model.dto.BillStatisticsQueryDTO;
import com.example.novusapirouter.model.vo.BillStatisticsVO;
import com.example.novusapirouter.server.service.BillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bill")
@RequiredArgsConstructor
public class BillController {

    private final BillService billService;

    @GetMapping("/statistics")
    public Result<BillStatisticsVO> getStatistics(@Valid @ModelAttribute BillStatisticsQueryDTO queryDTO) {
        return Result.success(billService.getStatistics(queryDTO));
    }
}
