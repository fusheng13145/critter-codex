package com.just.critter.controller.fund;

import com.just.critter.domain.enums.ExpenseStatus;
import com.just.critter.service.fund.CreatedExpense;
import com.just.critter.service.fund.ExpenseFilter;
import com.just.critter.service.fund.ExpenseReviewResult;
import com.just.critter.service.fund.ExpenseService;
import com.just.critter.service.fund.ExpenseSummary;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.api.Result;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.security.AuthContext;
import com.just.critter.support.security.CurrentUser;
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
 * 支出接口（03 §7.9—§7.10）：登记、列表、复核。全部仅动物管理员可访问。
 */
@RestController
@RequestMapping("/api/v1/expenses")
public class ExpenseController {

    private final ExpenseService service;

    public ExpenseController(ExpenseService service) {
        this.service = service;
    }

    @PostMapping
    @RequireRoles({"ANIMAL_ADMIN"})
    public Result<CreatedExpense> create(@RequestBody(required = false) CreateExpenseRequest request) {
        CurrentUser actor = requireAnimalAdmin();
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请求体不能为空");
        }
        return Result.ok(service.create(request.toCommand(), actor));
    }

    @GetMapping
    @RequireRoles({"ANIMAL_ADMIN"})
    public Result<PageResult<ExpenseSummary>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String projectNo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAnimalAdmin();
        ExpenseFilter filter = new ExpenseFilter(parseStatuses(status), projectNo);
        return Result.ok(service.search(filter, new PageQuery(page, size)));
    }

    @PutMapping("/{expenseNo}/review")
    @RequireRoles({"ANIMAL_ADMIN"})
    public Result<ExpenseReviewResult> review(@PathVariable String expenseNo,
                                              @RequestBody(required = false) ReviewExpenseRequest request) {
        CurrentUser actor = requireAnimalAdmin();
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请求体不能为空");
        }
        return Result.ok(service.review(expenseNo, request.toCommand(), actor));
    }

    private CurrentUser requireAnimalAdmin() {
        CurrentUser current = AuthContext.require();
        if (!current.hasRole("ANIMAL_ADMIN")) {
            throw new BusinessException(ErrorCode.ROLE_MISMATCH, "仅动物管理员可操作支出");
        }
        return current;
    }

    private List<ExpenseStatus> parseStatuses(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> {
                    try {
                        return ExpenseStatus.valueOf(value.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ex) {
                        throw new BusinessException(ErrorCode.ENUM_INVALID, "支出状态非法：" + value);
                    }
                })
                .toList();
    }
}
