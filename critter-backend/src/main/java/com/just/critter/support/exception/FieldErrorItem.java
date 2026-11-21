package com.just.critter.support.exception;

/**
 * 字段级校验错误，前端据此定位到具体输入框。
 */
public record FieldErrorItem(String field, String reason) {
}
