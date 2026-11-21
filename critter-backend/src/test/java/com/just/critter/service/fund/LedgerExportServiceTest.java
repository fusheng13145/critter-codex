package com.just.critter.service.fund;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.just.critter.config.CritterProperties;
import com.just.critter.support.cache.KeyValueStore;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 台账导出：同步分支必须产出可被 Excel 打开的 xlsx，且汇总与流水同表。
 */
@ExtendWith(MockitoExtension.class)
class LedgerExportServiceTest {

    @Mock
    private LedgerService ledgerService;
    @Mock
    private KeyValueStore store;

    @Test
    @DisplayName("五万行以内：同步返回 xlsx，含汇总与逐笔流水")
    void export_returns_xlsx_synchronously() throws Exception {
        LocalDate from = LocalDate.parse("2026-11-01");
        LocalDate to = LocalDate.parse("2026-11-21");
        LedgerEntry entry = new LedgerEntry("INCOME", "DN20261121001", "PRJ-2026-001",
                "校园流浪猫绝育计划", "爱心捐赠", "张同学", null, 10_000, "VERIFIED",
                OffsetDateTime.parse("2026-11-20T09:30:00+08:00"));
        when(ledgerService.ledger(null, from, to)).thenReturn(
                new LedgerResult(new LedgerSummary(10_000, 2_000, 8_000, 1, 1), List.of(entry)));

        LedgerExportService service = new LedgerExportService(
                ledgerService, store, new ObjectMapper(), new CritterProperties());
        LedgerExportService.ExportOutcome outcome = service.export(null, from, to);

        assertThat(outcome.isAsync()).isFalse();
        assertThat(outcome.fileName()).endsWith(".xlsx");
        assertThat(outcome.bytes()).isNotEmpty();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(outcome.bytes()))) {
            assertThat(workbook.getNumberOfSheets()).isGreaterThanOrEqualTo(1);
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isGreaterThan(0);
        }
    }
}
