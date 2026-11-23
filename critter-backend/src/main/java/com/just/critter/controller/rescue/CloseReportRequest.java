package com.just.critter.controller.rescue;

/**
 * 关闭上报请求。invalid=true 时将上报标记为无效，不计入效率统计分母。
 */
public record CloseReportRequest(String reason, Boolean invalid) {

    public boolean invalidFlag() {
        return Boolean.TRUE.equals(invalid);
    }
}
