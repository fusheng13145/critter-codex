package com.just.critter.service.fund;

/**
 * 批量失败项：id 为捐赠单号，code/message 沿用业务错误码。
 */
public record BatchFailure(String id, int code, String message) {
}
