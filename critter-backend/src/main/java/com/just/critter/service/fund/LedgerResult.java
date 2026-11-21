package com.just.critter.service.fund;

import java.util.List;

/**
 * 资金台账：汇总 + 逐笔流水。
 */
public record LedgerResult(LedgerSummary summary, List<LedgerEntry> entries) {
}
