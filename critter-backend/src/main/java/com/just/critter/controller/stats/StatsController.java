package com.just.critter.controller.stats;

import com.just.critter.controller.dto.StatsDtos;
import com.just.critter.service.stats.StatsExportService;
import com.just.critter.service.stats.StatsService;
import com.just.critter.support.api.Result;
import com.just.critter.support.security.RequireRoles;
import org.springframework.format.annotation.DateTimeFormat;
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

/**
 * 统计接口（接口设计 §6.3 第 40—43 项）。
 * 权限按文档：生物总览登录可见；资金、效率与导出只给管理员（系统管理员可读，不参与复核）。
 */
@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final StatsService statsService;
    private final StatsExportService exportService;

    public StatsController(StatsService statsService, StatsExportService exportService) {
        this.statsService = statsService;
        this.exportService = exportService;
    }

    @GetMapping("/biology")
    public Result<StatsDtos.BiologyResponse> biology() {
        return Result.ok(statsService.biology());
    }

    @GetMapping("/fund")
    @RequireRoles({"ANIMAL_ADMIN", "SYS_ADMIN", "SYSTEM_ADMIN"})
    public Result<StatsDtos.FundResponse> fund(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(statsService.fund(from, to));
    }

    @GetMapping("/efficiency")
    @RequireRoles({"ANIMAL_ADMIN", "SYS_ADMIN", "SYSTEM_ADMIN"})
    public Result<StatsDtos.EfficiencyResponse> efficiency(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(statsService.efficiency(from, to));
    }

    /**
     * 统计导出。5 万行以内直接回 xlsx 二进制（异常时仍回统一信封），
     * 超过则回 {@code {taskId, pollUrl}}，由前端轮询 /tasks/export/{taskId}。
     */
    @GetMapping("/export")
    @RequireRoles({"ANIMAL_ADMIN", "SYS_ADMIN", "SYSTEM_ADMIN"})
    public ResponseEntity<?> export(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        StatsExportService.ExportOutcome outcome = exportService.export(from, to);
        if (outcome.isAsync()) {
            return ResponseEntity.ok(Result.ok(outcome.task()));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment(outcome.fileName()))
                .contentType(XLSX)
                .body(outcome.bytes());
    }

    /** 异步导出结果下载（文档只写了取下载地址，这是地址指向的实际接口）。 */
    @GetMapping("/export/{taskId}/download")
    @RequireRoles({"ANIMAL_ADMIN", "SYS_ADMIN", "SYSTEM_ADMIN"})
    public ResponseEntity<byte[]> download(@PathVariable String taskId) {
        byte[] bytes = exportService.download(taskId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment(exportService.fileNameOf(taskId)))
                .contentType(XLSX)
                .body(bytes);
    }

    private static String attachment(String fileName) {
        return ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8)
                .build()
                .toString();
    }
}
