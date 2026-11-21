package com.just.critter.support.api;

import com.just.critter.support.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验收标准（接口设计说明书 §2）：所有接口（含错误）返回同一结构
 * {code, message, data, requestId, timestamp}，业务结果只看 code。
 */
class ResultTest {

    @Test
    @DisplayName("成功响应 code=0、message=ok、data 为空")
    void success_without_data() {
        Result<Void> result = Result.ok();

        assertThat(result.getCode()).isZero();
        assertThat(result.getMessage()).isEqualTo("ok");
        assertThat(result.getData()).isNull();
        assertThat(result.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("成功响应携带业务数据")
    void success_with_data() {
        Result<String> result = Result.ok("CAT-2026-031");

        assertThat(result.getCode()).isZero();
        assertThat(result.getData()).isEqualTo("CAT-2026-031");
    }

    @Test
    @DisplayName("失败响应取错误码的默认文案")
    void failure_uses_error_code_default_message() {
        Result<Void> result = Result.fail(ErrorCode.RECORD_NOT_FOUND);

        assertThat(result.getCode()).isEqualTo(30410);
        assertThat(result.getMessage()).isEqualTo(ErrorCode.RECORD_NOT_FOUND.getMessage());
        assertThat(result.getData()).isNull();
    }

    @Test
    @DisplayName("失败响应可携带业务细节，如余额差额")
    void failure_keeps_business_payload() {
        Result<Object> result = Result.fail(ErrorCode.BALANCE_NOT_ENOUGH, 20000);

        assertThat(result.getCode()).isEqualTo(40103);
        assertThat(result.getData()).isEqualTo(20000);
    }

    @Test
    @DisplayName("失败响应支持自定义码与文案")
    void failure_supports_custom_code_and_message() {
        Result<Void> result = Result.fail(40105, "存在未复核支出，无法结项", null);

        assertThat(result.getCode()).isEqualTo(40105);
        assertThat(result.getMessage()).isEqualTo("存在未复核支出，无法结项");
    }

    @Test
    @DisplayName("时间戳带时区偏移，符合 ISO-8601")
    void timestamp_is_offset_date_time() {
        Result<Void> result = Result.ok();

        OffsetDateTime timestamp = result.getTimestamp();
        assertThat(timestamp).isInstanceOf(OffsetDateTime.class);
        assertThat(OffsetDateTime.parse(timestamp.toString())).isEqualTo(timestamp);
        assertThat(timestamp.getOffset()).isNotNull();
    }

    @Test
    @DisplayName("requestId 可由链路追踪填充")
    void request_id_can_be_attached() {
        Result<Void> result = Result.ok().withRequestId("b1f0c3d2");

        assertThat(result.getRequestId()).isEqualTo("b1f0c3d2");
    }
}
