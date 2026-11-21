package com.just.critter.support.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验收标准（概要设计 §6）：业务异常必须带码，禁止 RuntimeException("失败")，
 * 异常携带的数据要能透传到统一响应体。
 */
class BusinessExceptionTest {

    @Test
    @DisplayName("用错误码构造时取默认文案")
    void uses_error_code_defaults() {
        BusinessException ex = new BusinessException(ErrorCode.RECORD_NOT_FOUND);

        assertThat(ex.getCode()).isEqualTo(30410);
        assertThat(ex.getMessage()).isEqualTo(ErrorCode.RECORD_NOT_FOUND.getMessage());
        assertThat(ex.getData()).isNull();
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("业务细节随异常透传，如余额差额")
    void carries_business_data() {
        BusinessException ex = new BusinessException(ErrorCode.BALANCE_NOT_ENOUGH, 20000);

        assertThat(ex.getCode()).isEqualTo(40103);
        assertThat(ex.getData()).isEqualTo(20000);
    }

    @Test
    @DisplayName("支持自定义码与文案")
    void supports_custom_code() {
        BusinessException ex = new BusinessException(40105, "存在未复核支出，无法结项");

        assertThat(ex.getCode()).isEqualTo(40105);
        assertThat(ex.getMessage()).isEqualTo("存在未复核支出，无法结项");
    }

    @Test
    @DisplayName("空白文案回退到错误码默认文案")
    void blank_message_falls_back() {
        BusinessException ex = new BusinessException(40105, "   ");

        assertThat(ex.getMessage()).isEqualTo(ErrorCode.UNREVIEWED_EXPENSE_EXISTS.getMessage());
    }
}
