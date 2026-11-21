package com.just.critter.service.stats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.just.critter.config.CritterProperties;
import com.just.critter.controller.dto.ExportDtos;
import com.just.critter.controller.dto.StatsDtos;
import com.just.critter.mapper.StatsMapper;
import com.just.critter.support.cache.MemoryKeyValueStore;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

/**
 * 统计导出：阈值内同步回 xlsx；超阈值走 taskId + 轮询；下载不存在的任务 30410。
 * 阈值是配置项，测试调到 0 即可覆盖异步分支，不必真造 5 万行。
 */
@ExtendWith(MockitoExtension.class)
class StatsExportServiceTest {

    @Mock
    private StatsService statsService;
    @Mock
    private StatsMapper statsMapper;

    @TempDir
    Path exportDir;

    private final CritterProperties properties = new CritterProperties();
    private StatsExportService service;

    @BeforeEach
    void setUp() {
        properties.getExport().setDir(exportDir.toString());
        properties.getExport().setTaskTtlSeconds(600);
        service = new StatsExportService(statsService, statsMapper, new MemoryKeyValueStore(),
                new ObjectMapper(), properties);

        lenient().when(statsMapper.animalTotal()).thenReturn(12L);
        lenient().when(statsMapper.groupTotal()).thenReturn(3L);
        lenient().when(statsMapper.occurrenceTotal()).thenReturn(0L);
        lenient().when(statsService.biology()).thenReturn(biology());
        lenient().when(statsService.fund(null, null)).thenReturn(fund());
        lenient().when(statsService.efficiency(null, null)).thenReturn(efficiency());
    }

    @Test
    @DisplayName("阈值内：同步返回 xlsx 字节，文件名带时间戳")
    void export_within_threshold_returns_xlsx_bytes() {
        StatsExportService.ExportOutcome outcome = service.export(null, null);

        assertThat(outcome.isAsync()).isFalse();
        assertThat(outcome.bytes()).isNotEmpty();
        // xlsx 是 zip 容器，前两字节固定 PK
        assertThat(new String(outcome.bytes(), 0, 2)).isEqualTo("PK");
        assertThat(outcome.fileName()).endsWith(".xlsx");
    }

    @Test
    @DisplayName("超阈值：返回 taskId，轮询到 DONE 后可下载")
    void export_over_threshold_runs_async_and_can_download() throws Exception {
        properties.getExport().setAsyncThreshold(0L);

        StatsExportService.ExportOutcome outcome = service.export(null, null);

        assertThat(outcome.isAsync()).isTrue();
        assertThat(outcome.task().pollUrl()).contains("/tasks/export/");
        String taskId = outcome.task().taskId();

        ExportDtos.ExportTaskView view = awaitDone(taskId);
        assertThat(view.status()).isEqualTo("DONE");
        assertThat(view.downloadUrl()).contains(taskId);

        byte[] bytes = service.download(taskId);
        assertThat(new String(bytes, 0, 2)).isEqualTo("PK");
    }

    @Test
    @DisplayName("下载不存在的任务：30410")
    void download_unknown_task_is_30410() {
        assertThatThrownBy(() -> service.download("not-exists"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.RECORD_NOT_FOUND.getCode());
    }

    private ExportDtos.ExportTaskView awaitDone(String taskId) throws InterruptedException {
        for (int i = 0; i < 60; i++) {
            ExportDtos.ExportTaskView view = service.task(taskId);
            if (!"RUNNING".equals(view.status())) {
                return view;
            }
            Thread.sleep(50);
        }
        return service.task(taskId);
    }


    private static StatsDtos.BiologyResponse biology() {
        return new StatsDtos.BiologyResponse(12, 1,
                List.of(new StatsDtos.StatusSlice("ON_CAMPUS", "在校园活动", 11)),
                3, 25.0, 2, 16.6, 4, 10, 30, 7,
                List.of(new StatsDtos.MonthPoint("2026-11", 2)));
    }

    private static StatsDtos.FundResponse fund() {
        return new StatsDtos.FundResponse(
                new StatsDtos.FundSummary(100_000, 40_000, 60_000, 5, 3),
                List.of(new StatsDtos.FundMonth("2026-11", 100_000, 40_000)),
                List.of(new StatsDtos.FundProject("P2026-01", "猫屋计划", "RAISING",
                        200_000, 100_000, 40_000, 60_000, 50.0)),
                List.of());
    }

    private static StatsDtos.EfficiencyResponse efficiency() {
        return new StatsDtos.EfficiencyResponse(
                new StatsDtos.ReportEfficiency(10, 2, 3, 5),
                new StatsDtos.OrderEfficiency(8, 5, 1, 2, 45, 62.5),
                1, 2);
    }
}
