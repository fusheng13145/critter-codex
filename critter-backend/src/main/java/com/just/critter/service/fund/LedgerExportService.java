package com.just.critter.service.fund;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.just.critter.config.CritterProperties;
import com.just.critter.controller.dto.ExportDtos;
import com.just.critter.support.cache.KeyValueStore;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import jakarta.annotation.PreDestroy;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
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
 * 资金台账导出（GET /ledger/export）。与页面共用 {@link LedgerService} 的同一份口径。
 * 5 万行以内同步返回 xlsx；超过阈值写入异步任务，状态存 {@link KeyValueStore}，
 * 前端轮询 /ledger/export/{taskId} 后从 downloadUrl 下载。
 */
@Service
public class LedgerExportService {

    private static final Logger log = LoggerFactory.getLogger(LedgerExportService.class);
    private static final String TASK_PREFIX = "ledger:export:";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LedgerService ledgerService;
    private final KeyValueStore store;
    private final ObjectMapper objectMapper;
    private final CritterProperties properties;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "ledger-export");
        thread.setDaemon(true);
        return thread;
    });

    public LedgerExportService(LedgerService ledgerService, KeyValueStore store,
                               ObjectMapper objectMapper, CritterProperties properties) {
        this.ledgerService = ledgerService;
        this.store = store;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public record ExportOutcome(byte[] bytes, String fileName, ExportDtos.ExportAccepted task) {
        public boolean isAsync() {
            return task != null;
        }
    }

    public record TaskState(String taskId, String status, long rows, String fileName,
                            String error, String createdAt) {
    }

    public ExportOutcome export(String projectNo, LocalDate from, LocalDate to) {
        LedgerResult ledger = ledgerService.ledger(projectNo, from, to);
        String fileName = "青檐万灵册-资金台账-" + LocalDateTime.now().format(STAMP) + ".xlsx";
        if (ledger.entries().size() <= properties.getExport().getAsyncThreshold()) {
            return new ExportOutcome(buildWorkbook(ledger), fileName, null);
        }
        String taskId = UUID.randomUUID().toString().replace("-", "");
        saveTask(new TaskState(taskId, "RUNNING", ledger.entries().size(), fileName, null,
                LocalDateTime.now().toString()));
        executor.submit(() -> runTask(taskId, fileName, ledger));
        return new ExportOutcome(null, fileName,
                new ExportDtos.ExportAccepted(taskId, "RUNNING", ledger.entries().size(),
                        "/api/v1/ledger/export/" + taskId));
    }

    public ExportDtos.ExportTaskView task(String taskId) {
        TaskState state = loadTask(taskId);
        String downloadUrl = "DONE".equals(state.status())
                ? "/api/v1/ledger/export/" + taskId + "/download" : null;
        return new ExportDtos.ExportTaskView(state.taskId(), state.status(), state.rows(),
                state.fileName(), downloadUrl, state.error());
    }

    public byte[] download(String taskId) {
        TaskState state = loadTask(taskId);
        if (!"DONE".equals(state.status())) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND.getCode(), "导出任务尚未完成或已失败");
        }
        Path file = Path.of(properties.getExport().getDir(), "ledger-" + taskId + ".xlsx");
        if (!Files.exists(file)) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND.getCode(), "导出文件已过期，请重新导出");
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException ex) {
            log.error("读取台账导出文件失败 taskId={}", taskId, ex);
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR);
        }
    }

    public String fileNameOf(String taskId) {
        return loadTask(taskId).fileName();
    }

    private void runTask(String taskId, String fileName, LedgerResult ledger) {
        try {
            byte[] bytes = buildWorkbook(ledger);
            Path dir = Path.of(properties.getExport().getDir());
            Files.createDirectories(dir);
            Files.write(dir.resolve("ledger-" + taskId + ".xlsx"), bytes);
            saveTask(new TaskState(taskId, "DONE", ledger.entries().size(), fileName, null,
                    LocalDateTime.now().toString()));
        } catch (Exception ex) {
            log.error("台账异步导出失败 taskId={}", taskId, ex);
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

    private byte[] buildWorkbook(LedgerResult ledger) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("资金台账");
            CellStyle title = titleStyle(workbook);
            CellStyle header = headerStyle(workbook);
            CellStyle money = moneyStyle(workbook);

            Row titleRow = sheet.createRow(0);
            titleRow.createCell(0).setCellValue("青檐万灵册 · 资金台账");
            titleRow.getCell(0).setCellStyle(title);

            LedgerSummary summary = ledger.summary();
            Row summaryRow = sheet.createRow(2);
            summaryRow.createCell(0).setCellValue("收入（元）");
            summaryRow.createCell(1).setCellValue(money(summary.income()));
            summaryRow.createCell(2).setCellValue("支出（元）");
            summaryRow.createCell(3).setCellValue(money(summary.expense()));
            summaryRow.createCell(4).setCellValue("结余（元）");
            summaryRow.createCell(5).setCellValue(money(summary.balance()));
            summaryRow.createCell(6).setCellValue("笔数");
            summaryRow.createCell(7).setCellValue(summary.donationCount() + summary.expenseCount());

            String[] columns = {"序号", "类型", "流水号", "项目编号", "项目名称", "摘要",
                    "对方", "动物编号", "金额（元）", "状态", "发生时间"};
            Row head = sheet.createRow(4);
            for (int i = 0; i < columns.length; i++) {
                head.createCell(i).setCellValue(columns[i]);
                head.getCell(i).setCellStyle(header);
            }

            int rowNo = 5;
            for (LedgerEntry entry : ledger.entries()) {
                Row row = sheet.createRow(rowNo++);
                row.createCell(0).setCellValue(rowNo - 5);
                row.createCell(1).setCellValue("INCOME".equals(entry.type()) ? "收入" : "支出");
                row.createCell(2).setCellValue(text(entry.refNo()));
                row.createCell(3).setCellValue(text(entry.projectNo()));
                row.createCell(4).setCellValue(text(entry.projectName()));
                row.createCell(5).setCellValue(text(entry.title()));
                row.createCell(6).setCellValue(text(entry.party()));
                row.createCell(7).setCellValue(text(entry.animalCode()));
                row.createCell(8).setCellValue(money(entry.amount()));
                row.getCell(8).setCellStyle(money);
                row.createCell(9).setCellValue(text(entry.status()));
                row.createCell(10).setCellValue(entry.occurredAt() == null
                        ? "" : entry.occurredAt().format(TIME));
            }

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.min(6000, sheet.getColumnWidth(i) + 800));
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "台账导出生成失败");
        }
    }

    private CellStyle titleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        style.setFont(font);
        return style;
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle moneyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
        return style;
    }

    private double money(long cents) {
        return BigDecimal.valueOf(cents, 2).doubleValue();
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
