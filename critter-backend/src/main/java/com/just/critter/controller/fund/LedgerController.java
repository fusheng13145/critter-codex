package com.just.critter.controller.fund;

import com.just.critter.service.fund.LedgerResult;
import com.just.critter.service.fund.LedgerService;
import com.just.critter.support.api.Result;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.security.RequireRoles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 资金台账接口（03 §7.11 GET /ledger）：管理员只读，收入与支出合并成逐笔流水。
 */
@RestController
@RequestMapping("/api/v1/ledger")
public class LedgerController {

    private final LedgerService service;

    public LedgerController(LedgerService service) {
        this.service = service;
    }

    @GetMapping
    @RequireRoles({"ANIMAL_ADMIN", "SYSTEM_ADMIN"})
    public Result<LedgerResult> ledger(
            @RequestParam(required = false) String projectNo,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return Result.ok(service.ledger(projectNo, parseDate(from, "from"), parseDate(to, "to")));
    }

    private LocalDate parseDate(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, field + " 需为 yyyy-MM-dd");
        }
    }
}
