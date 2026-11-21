package com.just.critter.controller.fund;

import com.just.critter.service.fund.AppendSettlementNoteCommand;

/**
 * 公示追加说明请求。只能追加，不能修改公示正文。
 */
public record AppendSettlementNoteRequest(String note) {

    public AppendSettlementNoteCommand toCommand() {
        return new AppendSettlementNoteCommand(note);
    }
}
