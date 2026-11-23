package com.just.critter.controller.rescue;

import com.just.critter.domain.enums.ReportStatus;
import com.just.critter.domain.enums.ReportType;
import com.just.critter.service.rescue.AcceptedReport;
import com.just.critter.service.rescue.ClosedReport;
import com.just.critter.service.rescue.CreatedReport;
import com.just.critter.service.rescue.ReportDetail;
import com.just.critter.service.rescue.ReportConfirmation;
import com.just.critter.service.rescue.ReportFilter;
import com.just.critter.service.rescue.ReportService;
import com.just.critter.service.rescue.ReportSummary;
import com.just.critter.service.rescue.RescueOrderService;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.api.Result;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.security.AuthContext;
import com.just.critter.support.security.RequireRoles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 上报接口（03 §6.2 #28—#32）。提交登录；列表管理员；详情登录；受理/关闭管理员。
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService service;
    private final RescueOrderService orderService;

    public ReportController(ReportService service, RescueOrderService orderService) {
        this.service = service;
        this.orderService = orderService;
    }

    @PostMapping
    public Result<CreatedReport> create(@RequestBody CreateReportRequest request) {
        return Result.ok(service.create(request.toCommand(), AuthContext.require().id()));
    }

    @GetMapping
    @RequireRoles({"ANIMAL_ADMIN", "SYSTEM_ADMIN"})
    public Result<PageResult<ReportSummary>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long pointId,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        ReportFilter filter = new ReportFilter(parseStatuses(status), parseType(type), pointId, overdue);
        return Result.ok(service.search(filter, new PageQuery(page, size)));
    }

    @GetMapping("/mine")
    public Result<PageResult<ReportSummary>> mine(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.mine(AuthContext.require().id(), new PageQuery(page, size)));
    }

    @GetMapping("/{id}")
    public Result<ReportDetail> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id, AuthContext.require()));
    }

    @PostMapping("/{id}/accept")
    @RequireRoles({"ANIMAL_ADMIN"})
    public Result<AcceptedReport> accept(@PathVariable Long id,
                                         @RequestBody AcceptReportRequest request) {
        return Result.ok(orderService.acceptReport(id, request.toCommand(), AuthContext.require()));
    }

    @PutMapping("/{id}/close")
    @RequireRoles({"ANIMAL_ADMIN"})
    public Result<ClosedReport> close(@PathVariable Long id, @RequestBody CloseReportRequest request) {
        return Result.ok(service.close(id, request.reason(), request.invalidFlag()));
    }

    @PutMapping("/{id}/confirm")
    public Result<ReportConfirmation> confirm(@PathVariable Long id) {
        return Result.ok(service.confirm(id, AuthContext.require().id()));
    }

    private List<ReportStatus> parseStatuses(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return Arrays.stream(status.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> {
                    try {
                        return ReportStatus.valueOf(value.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ex) {
                        throw new BusinessException(30303, "上报状态非法：" + value);
                    }
                })
                .toList();
    }

    private ReportType parseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return ReportType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(30303, "上报类型非法：" + type);
        }
    }
}
