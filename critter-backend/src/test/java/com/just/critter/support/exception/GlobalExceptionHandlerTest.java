package com.just.critter.support.exception;

import com.just.critter.support.api.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验收标准（概要设计 §6、接口设计说明书 §2）：三层异常统一收敛为 code/message/data，
 * 校验失败按字段聚合，系统异常不把堆栈或异常类名返回前端。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("业务异常按原码返回，细节保留")
    void business_exception_is_translated() {
        Result<?> result = handler.handleBusiness(new BusinessException(ErrorCode.BALANCE_NOT_ENOUGH, 20000));

        assertThat(result.getCode()).isEqualTo(40103);
        assertThat(result.getMessage()).isEqualTo(ErrorCode.BALANCE_NOT_ENOUGH.getMessage());
        assertThat(result.getData()).isEqualTo(20000);
    }

    @Test
    @DisplayName("参数校验失败收敛为 30301，并按字段聚合")
    void validation_errors_are_grouped_by_field() throws NoSuchMethodException {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new AnimalForm(), "request");
        binding.rejectValue("estimatedAge", "range", "必须在 0—25 之间");

        Method method = SampleRequest.class.getDeclaredMethod("handle", Object.class);
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(new MethodParameter(method, 0), binding);

        Result<?> result = handler.handleValidation(ex);

        assertThat(result.getCode()).isEqualTo(30301);
        assertThat(result.getData()).isInstanceOf(List.class);
        List<?> errors = (List<?>) result.getData();
        assertThat(errors).hasSize(1);

        FieldErrorItem item = (FieldErrorItem) errors.get(0);
        assertThat(item.field()).isEqualTo("estimatedAge");
        assertThat(item.reason()).isEqualTo("必须在 0—25 之间");
    }

    @Test
    @DisplayName("未捕获异常统一转为 50001，且不泄漏异常类名与堆栈")
    void uncaught_exception_is_masked() {
        Result<?> result = handler.handleUncaught(new IllegalStateException("db connection refused"));

        assertThat(result.getCode()).isEqualTo(50001);
        assertThat(result.getMessage()).isEqualTo(ErrorCode.SYSTEM_ERROR.getMessage());
        assertThat(result.getMessage()).doesNotContain("IllegalStateException", "db connection refused");
        assertThat(result.getData()).isNull();
    }

    static class SampleRequest {
        @SuppressWarnings("unused")
        void handle(Object body) {
        }
    }

    static class AnimalForm {

        private Integer estimatedAge;

        public Integer getEstimatedAge() {
            return estimatedAge;
        }

        public void setEstimatedAge(Integer estimatedAge) {
            this.estimatedAge = estimatedAge;
        }
    }
}
