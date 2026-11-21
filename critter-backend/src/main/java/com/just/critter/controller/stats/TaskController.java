package com.just.critter.controller.stats;

import com.just.critter.controller.dto.ExportDtos;
import com.just.critter.service.stats.StatsExportService;
import com.just.critter.support.api.Result;
import com.just.critter.support.security.RequireRoles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 异步任务查询（接口设计 §8「导出」约定）：前端拿 taskId 轮询这里取下载地址。
 * 目前只有导出任务，路径仍按 {@code /tasks/export/{taskId}} 预留，后续其它异步任务可直接挂。
 */
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final StatsExportService exportService;

    public TaskController(StatsExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/export/{taskId}")
    @RequireRoles({"ANIMAL_ADMIN", "SYS_ADMIN", "SYSTEM_ADMIN"})
    public Result<ExportDtos.ExportTaskView> exportTask(@PathVariable String taskId) {
        return Result.ok(exportService.task(taskId));
    }
}
