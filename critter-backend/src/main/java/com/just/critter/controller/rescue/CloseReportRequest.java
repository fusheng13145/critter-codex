package com.just.critter.controller.rescue;

/**
 * 关闭无效上报请求，reason 必填。
 */
public record CloseReportRequest(String reason) {
}
