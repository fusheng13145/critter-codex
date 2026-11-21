package com.just.critter.controller.rescue;

import com.just.critter.domain.enums.OrderStatus;
import com.just.critter.domain.enums.ReportType;
import com.just.critter.service.rescue.OrderDetail;
import com.just.critter.service.rescue.OrderFilter;
import com.just.critter.service.rescue.OrderService;
import com.just.critter.service.rescue.OrderStatusCommand;
import com.just.critter.service.rescue.OrderSummary;
import com.just.critter.service.rescue.OrderTransitionResult;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.api.Result;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.security.AuthContext;
import com.just.critter.support.security.RequireRoles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 救助工单接口（03 §6.2 #33—#35）：列表/详情只读，状态流转按工单状态机表执行。
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @GetMapping
    @RequireRoles({"ANIMAL_ADMIN", "SYSTEM_ADMIN"})
    public Result<PageResult<OrderSummary>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        OrderFilter filter = new OrderFilter(parseStatuses(status), parseType(type), overdue);
        return Result.ok(service.search(filter, new PageQuery(page, size)));
    }

    @GetMapping("/{no}")
    public Result<OrderDetail> detail(@PathVariable String no) {
        return Result.ok(service.detail(no));
    }

    @PutMapping("/{no}/status")
    @RequireRoles({"ANIMAL_ADMIN"})
    public Result<OrderTransitionResult> transition(@PathVariable String no,
                                                     @RequestBody OrderStatusRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请求体不能为空");
        }
        OrderStatus target = parseTarget(request.targetStatus());
        OrderStatusCommand command = request.toCommand(target);
        return Result.ok(service.transition(no, command, AuthContext.require()));
    }

    private List<OrderStatus> parseStatuses(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return Arrays.stream(status.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> {
                    try {
                        return OrderStatus.valueOf(value.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ex) {
                        throw new BusinessException(ErrorCode.ENUM_INVALID, "工单状态非法：" + value);
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
            throw new BusinessException(ErrorCode.ENUM_INVALID, "上报类型非法：" + type);
        }
    }

    private OrderStatus parseTarget(String target) {
        if (target == null || target.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "目标状态必填");
        }
        try {
            return OrderStatus.valueOf(target.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.ENUM_INVALID, "工单状态非法：" + target);
        }
    }
}
