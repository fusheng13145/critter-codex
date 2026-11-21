package com.just.critter.controller.rescue;

import com.just.critter.service.rescue.CreatedReport;
import com.just.critter.service.rescue.ReportService;
import com.just.critter.support.api.Result;
import com.just.critter.support.security.AuthContext;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生上报接口（03 §6.2 #28—#32 的 #28）。提交仅要求登录；
 * 后续列表/详情/受理/关闭在后续轮次补齐。
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @PostMapping
    public Result<CreatedReport> create(@RequestBody CreateReportRequest request) {
        return Result.ok(service.create(request.toCommand(), AuthContext.require().id()));
    }
}
