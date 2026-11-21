package com.just.critter.controller.fund;

import com.just.critter.controller.dto.ExportDtos;
import com.just.critter.service.fund.LedgerExportService;
import com.just.critter.service.fund.LedgerResult;
import com.just.critter.service.fund.LedgerService;
import com.just.critter.support.api.Result;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.security.RequireRoles;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 资金台账与导出（03 §7.11）。导出与页面同源，5 万行以上转异步任务。
 */
@RestController
@RequestMapping("/api/v1/ledger")
public class LedgerController {

    private final LedgerService service;
    private final LedgerExportService exportService;

    public LedgerController(LedgerService service, LedgerExportService exportService) {
        this.service = service;
        this.exportService = exportService;
    }

    @GetMapping
    @RequireRoles({"ANIMAL_ADMIN", "SYSTEM_ADMIN"})
    public Result<LedgerResult> ledger(
            @RequestParam(required = false) String projectNo,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return Result.ok(service.ledger(projectNo, parseDate(from, "from"), parseDate(to, "to")));
    }

    @GetMapping("/export")
    @RequireRoles({"ANIMAL_ADMIN", "SYSTEM_ADMIN"})
    public ResponseEntity<?> export(
            @RequestParam(required = false) String projectNo,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LedgerExportService.ExportOutcome outcome =
                exportService.export(projectNo, parseDate(from, "from"), parseDate(to, "to"));
        if (outcome.isAsync()) {
            return ResponseEntity.ok(Result.ok(outcome.task()));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(outcome.fileName(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(outcome.bytes());
    }

    @GetMapping("/export/{taskId}")
    @RequireRoles({"ANIMAL_ADMIN", "SYSTEM_ADMIN"})
    public Result<ExportDtos.ExportTaskView> exportTask(@PathVariable String taskId) {
        return Result.ok(exportService.task(taskId));
    }

    @GetMapping("/export/{taskId}/download")
    @RequireRoles({"ANIMAL_ADMIN", "SYSTEM_ADMIN"})
    public ResponseEntity<byte[]> download(@PathVariable String taskId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(exportService.fileNameOf(taskId), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(exportService.download(taskId));
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
