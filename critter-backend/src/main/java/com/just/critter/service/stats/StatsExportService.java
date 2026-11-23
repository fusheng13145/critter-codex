package com.just.critter.service.stats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.just.critter.config.CritterProperties;
import com.just.critter.controller.dto.ExportDtos;
import com.just.critter.controller.dto.StatsDtos;
import com.just.critter.mapper.StatsMapper;
import com.just.critter.support.cache.KeyValueStore;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import jakarta.annotation.PreDestroy;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 统计导出（GET /stats/export）。
 *
 * <p>口径直接复用 {@link StatsService}，导出不复算——报表与页面必须同源，
 * 否则答辩时"页面 12 只、表格 11 只"很难解释。</p>
 *
 * <p>5 万行以内同步生成 xlsx 直接下载；超过走异步：任务状态存
 * {@link KeyValueStore}（1 小时），文件落 {@code critter.export.dir}，
 * 前端轮询 {@code /tasks/export/{taskId}} 拿下载地址。</p>
 */
@Service
public class StatsExportService {

    private static final Logger log = LoggerFactory.getLogger(StatsExportService.class);
    private static final String TASK_PREFIX = "export:task:";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private final StatsService statsService;
    private final StatsMapper statsMapper;
    private final KeyValueStore store;
    private final ObjectMapper objectMapper;
    private final CritterProperties properties;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "stats-export");
        thread.setDaemon(true);
        return thread;
    });

    public StatsExportService(StatsService statsService, StatsMapper statsMapper, KeyValueStore store,
                              ObjectMapper objectMapper, CritterProperties properties) {
        this.statsService = statsService;
        this.statsMapper = statsMapper;
        this.store = store;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /** 同步结果或异步受理二选一：bytes 非空表示同步，task 非空表示异步。 */
    public record ExportOutcome(byte[] bytes, String fileName, ExportDtos.ExportAccepted task) {
        public boolean isAsync() {
            return task != null;
        }
    }

    /** 任务状态（存 KeyValueStore，JSON 序列化）。 */
    public record TaskState(String taskId, String status, long rows, String fileName,
                            String error, String createdAt) {
    }

    public ExportOutcome export(LocalDate from, LocalDate to) {
        long rows = estimateRows();
        String fileName = "青檐万灵册-统计-" + LocalDateTime.now().format(STAMP) + ".xlsx";
        if (rows <= properties.getExport().getAsyncThreshold()) {
            return new ExportOutcome(buildWorkbook(from, to), fileName, null);
        }

        String taskId = UUID.randomUUID().toString().replace("-", "");
        TaskState running = new TaskState(taskId, "RUNNING", rows, fileName, null,
                LocalDateTime.now().toString());
        saveTask(running);
        executor.submit(() -> runTask(taskId, fileName, from, to));
        return new ExportOutcome(null, fileName,
                new ExportDtos.ExportAccepted(taskId, "RUNNING", rows, "/api/v1/tasks/export/" + taskId));
    }

    public ExportDtos.ExportTaskView task(String taskId) {
        TaskState state = loadTask(taskId);
        String downloadUrl = "DONE".equals(state.status())
                ? "/api/v1/stats/export/" + taskId + "/download"
                : null;
        return new ExportDtos.ExportTaskView(state.taskId(), state.status(), state.rows(),
                state.fileName(), downloadUrl, state.error());
    }

    /** 异步结果下载：只认 DONE 且文件仍在磁盘上的任务。 */
    public byte[] download(String taskId) {
        TaskState state = loadTask(taskId);
        if (!"DONE".equals(state.status())) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND.getCode(), "导出任务尚未完成或已失败");
        }
        Path file = Path.of(properties.getExport().getDir(), taskId + ".xlsx");
        if (!Files.exists(file)) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND.getCode(), "导出文件已过期，请重新导出");
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException ex) {
            log.error("读取导出文件失败 taskId={}", taskId, ex);
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR);
        }
    }

    public String fileNameOf(String taskId) {
        return loadTask(taskId).fileName();
    }

    public long estimateRows() {
        return statsMapper.animalTotal() + statsMapper.groupTotal() + statsMapper.occurrenceTotal();
    }

    // ---------------- 内部 ----------------

    private void runTask(String taskId, String fileName, LocalDate from, LocalDate to) {
        try {
            byte[] bytes = buildWorkbook(from, to);
            Path dir = Path.of(properties.getExport().getDir());
            Files.createDirectories(dir);
            Files.write(dir.resolve(taskId + ".xlsx"), bytes);
            saveTask(new TaskState(taskId, "DONE", estimateRows(), fileName, null,
                    LocalDateTime.now().toString()));
        } catch (Exception ex) {
            log.error("异步导出失败 taskId={}", taskId, ex);
            saveTask(new TaskState(taskId, "FAILED", 0, fileName, ex.getMessage(),
                    LocalDateTime.now().toString()));
        }
    }

    private void saveTask(TaskState state) {
        try {
            store.set(TASK_PREFIX + state.taskId(), objectMapper.writeValueAsString(state),
                    Duration.ofSeconds(properties.getExport().getTaskTtlSeconds()));
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "导出任务状态写入失败");
        }
    }

    private TaskState loadTask(String taskId) {
        String json = store.get(TASK_PREFIX + taskId).orElse(null);
        if (json == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND.getCode(), "导出任务不存在或已过期");
        }
        try {
            return objectMapper.readValue(json, TaskState.class);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "导出任务状态无法解析");
        }
    }

    /** 四张表：生物总览、状态分布、资金总览、效率统计。金额一律按"元"输出并保留两位。 */
    private byte[] buildWorkbook(LocalDate from, LocalDate to) {
        StatsDtos.BiologyResponse biology = statsService.biology();
        StatsDtos.FundResponse fund = statsService.fund(from, to);
        StatsDtos.EfficiencyResponse efficiency = statsService.efficiency(from, to);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = headerStyle(workbook);

            Sheet overview = workbook.createSheet("生物总览");
            int rowIndex = 0;
            rowIndex = kv(overview, rowIndex, "指标", "数值", header);
            rowIndex = kv(overview, rowIndex, "在册总数", String.valueOf(biology.total()), null);
            rowIndex = kv(overview, rowIndex, "已离世", String.valueOf(biology.deceased()), null);
            rowIndex = kv(overview, rowIndex, "已绝育", String.valueOf(biology.neuteredCount()), null);
            rowIndex = kv(overview, rowIndex, "绝育率(%)", trim(biology.neuteredRate()), null);
            rowIndex = kv(overview, rowIndex, "认养覆盖数", String.valueOf(biology.activeAdoptions()), null);
            rowIndex = kv(overview, rowIndex, "认养覆盖率(%)", trim(biology.adoptionCoverage()), null);
            rowIndex = kv(overview, rowIndex, "群体数", String.valueOf(biology.groupCount()), null);
            rowIndex = kv(overview, rowIndex, "出现记录数", String.valueOf(biology.occurrenceCount()), null);
            for (StatsDtos.MonthPoint month : biology.months()) {
                rowIndex = kv(overview, rowIndex, "新增（" + month.month() + "）",
                        String.valueOf(month.newAnimals()), null);
            }
            overview.setColumnWidth(0, 22 * 256);
            overview.setColumnWidth(1, 16 * 256);

            Sheet statusSheet = workbook.createSheet("状态分布");
            int statusRow = 0;
            Row statusHeader = statusSheet.createRow(statusRow++);
            cell(statusHeader, 0, "状态码", header);
            cell(statusHeader, 1, "状态", header);
            cell(statusHeader, 2, "数量", header);
            for (StatsDtos.StatusSlice slice : biology.byStatus()) {
                Row row = statusSheet.createRow(statusRow++);
                cell(row, 0, slice.status(), null);
                cell(row, 1, slice.label(), null);
                cell(row, 2, String.valueOf(slice.count()), null);
            }
            statusSheet.setColumnWidth(0, 18 * 256);
            statusSheet.setColumnWidth(1, 18 * 256);

            Sheet fundSheet = workbook.createSheet("资金总览");
            int fundRow = 0;
            fundRow = kv(fundSheet, fundRow, "指标", "数值", header);
            fundRow = kv(fundSheet, fundRow, "已入账（元）", yuan(fund.summary().raisedAmount()), null);
            fundRow = kv(fundSheet, fundRow, "已支出（元）", yuan(fund.summary().spentAmount()), null);
            fundRow = kv(fundSheet, fundRow, "结余（元）", yuan(fund.summary().balanceAmount()), null);
            fundRow = kv(fundSheet, fundRow, "捐赠笔数", String.valueOf(fund.summary().donationCount()), null);
            fundRow = kv(fundSheet, fundRow, "支出笔数", String.valueOf(fund.summary().expenseCount()), null);
            fundRow++;
            Row monthHeader = fundSheet.createRow(fundRow++);
            cell(monthHeader, 0, "月份", header);
            cell(monthHeader, 1, "入账（元）", header);
            cell(monthHeader, 2, "支出（元）", header);
            for (StatsDtos.FundMonth month : fund.byMonth()) {
                Row row = fundSheet.createRow(fundRow++);
                cell(row, 0, month.month(), null);
                cell(row, 1, yuan(month.raisedAmount()), null);
                cell(row, 2, yuan(month.spentAmount()), null);
            }
            fundRow++;
            Row projectHeader = fundSheet.createRow(fundRow++);
            cell(projectHeader, 0, "项目编号", header);
            cell(projectHeader, 1, "项目名称", header);
            cell(projectHeader, 2, "状态", header);
            cell(projectHeader, 3, "目标（元）", header);
            cell(projectHeader, 4, "入账（元）", header);
            cell(projectHeader, 5, "支出（元）", header);
            cell(projectHeader, 6, "结余（元）", header);
            cell(projectHeader, 7, "进度(%)", header);
            for (StatsDtos.FundProject project : fund.byProject()) {
                Row row = fundSheet.createRow(fundRow++);
                cell(row, 0, project.no(), null);
                cell(row, 1, project.name(), null);
                cell(row, 2, project.status(), null);
                cell(row, 3, yuan(project.targetAmount()), null);
                cell(row, 4, yuan(project.raisedAmount()), null);
                cell(row, 5, yuan(project.spentAmount()), null);
                cell(row, 6, yuan(project.balanceAmount()), null);
                cell(row, 7, trim(project.progress()), null);
            }
            fundSheet.setColumnWidth(0, 22 * 256);
            fundSheet.setColumnWidth(1, 30 * 256);

            Sheet efficiencySheet = workbook.createSheet("效率统计");
            int effRow = 0;
            effRow = kv(efficiencySheet, effRow, "指标", "数值", header);
            effRow = kv(efficiencySheet, effRow, "上报总数", String.valueOf(efficiency.reports().total()), null);
            effRow = kv(efficiencySheet, effRow, "上报待处理", String.valueOf(efficiency.reports().pending()), null);
            effRow = kv(efficiencySheet, effRow, "上报处理中", String.valueOf(efficiency.reports().processing()), null);
            effRow = kv(efficiencySheet, effRow, "上报已结案", String.valueOf(efficiency.reports().closed()), null);
            effRow = kv(efficiencySheet, effRow, "无效上报（已排除）", String.valueOf(efficiency.reports().invalid()), null);
            effRow = kv(efficiencySheet, effRow, "工单总数", String.valueOf(efficiency.orders().total()), null);
            effRow = kv(efficiencySheet, effRow, "工单已结案", String.valueOf(efficiency.orders().closed()), null);
            effRow = kv(efficiencySheet, effRow, "工单超时", String.valueOf(efficiency.orders().timeout()), null);
            effRow = kv(efficiencySheet, effRow, "平均受理（分钟）",
                    String.valueOf(efficiency.orders().avgAcceptMinutes()), null);
            effRow = kv(efficiencySheet, effRow, "结案率(%)", trim(efficiency.orders().closeRate()), null);
            effRow = kv(efficiencySheet, effRow, "认养待办", String.valueOf(efficiency.pendingAdoptions()), null);
            effRow = kv(efficiencySheet, effRow, "支出待复核", String.valueOf(efficiency.pendingExpenses()), null);
            efficiencySheet.setColumnWidth(0, 22 * 256);
            efficiencySheet.setColumnWidth(1, 18 * 256);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            log.error("生成统计导出失败", ex);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "报表生成失败，请稍后重试");
        }
    }

    private static int kv(Sheet sheet, int rowIndex, String key, String value, CellStyle style) {
        Row row = sheet.createRow(rowIndex);
        cell(row, 0, key, style);
        cell(row, 1, value, null);
        return rowIndex + 1;
    }

    private static void cell(Row row, int index, String value, CellStyle style) {
        Cell current = row.createCell(index);
        current.setCellValue(value == null ? "" : value);
        if (style != null) {
            current.setCellStyle(style);
        }
    }

    private static CellStyle headerStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private static String yuan(long cents) {
        return String.format("%.2f", cents / 100.0);
    }

    private static String trim(double value) {
        return String.valueOf(Math.round(value * 10) / 10.0);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
