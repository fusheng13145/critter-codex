package com.just.critter.support.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验收标准（接口设计说明书 §3）：错误码按业务族分段，同码唯一，每条都带面向用户的中文提示。
 */
class ErrorCodeTest {

    @Test
    @DisplayName("错误码不重复")
    void codes_are_unique() {
        assertThat(Arrays.stream(ErrorCode.values()).map(ErrorCode::getCode).distinct().count())
                .isEqualTo(ErrorCode.values().length);
    }

    @Test
    @DisplayName("每条错误码都有非空提示，且提示不含英文术语")
    void messages_are_user_facing() {
        assertThat(ErrorCode.values())
                .allSatisfy(code -> {
                    assertThat(code.getMessage()).isNotBlank();
                    assertThat(code.getMessage()).doesNotContain("Exception", "null", "stack");
                });
    }

    @Test
    @DisplayName("关键错误码与文档一致")
    void key_codes_match_document() {
        assertThat(ErrorCode.SUCCESS.getCode()).isZero();
        assertThat(ErrorCode.SUCCESS.getMessage()).isEqualTo("ok");

        assertThat(ErrorCode.PARAM_INVALID.getCode()).isEqualTo(30301);
        assertThat(ErrorCode.RECORD_NOT_FOUND.getCode()).isEqualTo(30410);
        assertThat(ErrorCode.STATE_TRANSITION_INVALID.getCode()).isEqualTo(30402);
        assertThat(ErrorCode.GROUP_STATE_INVALID.getCode()).isEqualTo(30505);
        assertThat(ErrorCode.PROJECT_STATE_INVALID.getCode()).isEqualTo(40108);

        assertThat(ErrorCode.BALANCE_NOT_ENOUGH.getCode()).isEqualTo(40103);
        assertThat(ErrorCode.SELF_REVIEW_NOT_ALLOWED.getCode()).isEqualTo(40104);
        assertThat(ErrorCode.UNREVIEWED_EXPENSE_EXISTS.getCode()).isEqualTo(40105);

        assertThat(ErrorCode.SYSTEM_ERROR.getCode()).isEqualTo(50001);
    }

    @Test
    @DisplayName("可按码反查枚举，未知码返回空")
    void lookup_by_code() {
        assertThat(ErrorCode.fromCode(30410)).contains(ErrorCode.RECORD_NOT_FOUND);
        assertThat(ErrorCode.fromCode(99999)).isEmpty();
    }
}
