package com.just.critter.support.exception;

import com.just.critter.support.api.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验收标准（接口设计说明书 §2、§3）：统一响应壳；业务失败默认 HTTP 200，
 * 鉴权 401、权限 403、不存在 404、并发 409、系统异常 500 按码映射；
 * message 可直接展示，不泄漏异常类名与堆栈。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @Test
    @DisplayName("业务异常按原码返回，默认 HTTP 200，细节保留")
    void business_exception_is_translated() {
        ResponseEntity<Result<?>> entity = handler.handleBusiness(
                new BusinessException(ErrorCode.BALANCE_NOT_ENOUGH, 20000), request);

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entity.getBody().getCode()).isEqualTo(40103);
        assertThat(entity.getBody().getMessage()).isEqualTo(ErrorCode.BALANCE_NOT_ENOUGH.getMessage());
        assertThat(entity.getBody().getData()).isEqualTo(20000);
    }

    @Test
    @DisplayName("按错误码映射 HTTP：401/403/404/409/500，其余 200")
    void http_status_follows_error_code() {
        assertStatus(ErrorCode.NOT_LOGGED_IN, HttpStatus.UNAUTHORIZED);
        assertStatus(ErrorCode.TOKEN_INVALID, HttpStatus.UNAUTHORIZED);
        assertStatus(ErrorCode.NO_PERMISSION, HttpStatus.FORBIDDEN);
        assertStatus(ErrorCode.ROLE_MISMATCH, HttpStatus.FORBIDDEN);
        assertStatus(ErrorCode.FIELD_INVISIBLE, HttpStatus.FORBIDDEN);
        assertStatus(ErrorCode.RECORD_NOT_FOUND, HttpStatus.NOT_FOUND);
        assertStatus(ErrorCode.CONCURRENT_CONFLICT, HttpStatus.CONFLICT);
        assertStatus(ErrorCode.SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        assertStatus(ErrorCode.FILE_STORAGE_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        assertStatus(ErrorCode.PARAM_INVALID, HttpStatus.OK);
        assertStatus(ErrorCode.STATE_TRANSITION_INVALID, HttpStatus.OK);
    }

    private void assertStatus(ErrorCode code, HttpStatus expected) {
        ResponseEntity<Result<?>> entity = handler.handleBusiness(new BusinessException(code), request);
        assertThat(entity.getStatusCode()).as(code.name()).isEqualTo(expected);
        assertThat(entity.getBody().getCode()).isEqualTo(code.getCode());
    }

    @Test
    @DisplayName("响应体回填 requestId，便于按日志定位")
    void response_carries_request_id() {
        request.setAttribute(com.just.critter.support.api.RequestIdFilter.ATTRIBUTE, "req-abc");
        ResponseEntity<Result<?>> entity = handler.handleBusiness(
                new BusinessException(ErrorCode.SYSTEM_ERROR), request);

        assertThat(entity.getBody().getRequestId()).isEqualTo("req-abc");
    }

    @Test
    @DisplayName("参数校验失败收敛为 30301，并按字段聚合")
    void validation_errors_are_grouped_by_field() throws NoSuchMethodException {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new AnimalForm(), "request");
        binding.rejectValue("estimatedAge", "range", "必须在 0—25 之间");

        Method method = SampleRequest.class.getDeclaredMethod("handle", Object.class);
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(new MethodParameter(method, 0), binding);

        ResponseEntity<Result<?>> entity = handler.handleValidation(ex, request);

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entity.getBody().getCode()).isEqualTo(30301);
        assertThat(entity.getBody().getData()).isInstanceOf(List.class);
        List<?> errors = (List<?>) entity.getBody().getData();
        assertThat(errors).hasSize(1);

        FieldErrorItem item = (FieldErrorItem) errors.get(0);
        assertThat(item.field()).isEqualTo("estimatedAge");
        assertThat(item.reason()).isEqualTo("必须在 0—25 之间");
    }

    @Test
    @DisplayName("未捕获异常统一转为 50001 + HTTP 500，且不泄漏异常类名与堆栈")
    void uncaught_exception_is_masked() {
        ResponseEntity<Result<?>> entity = handler.handleUncaught(
                new IllegalStateException("db connection refused"), request);

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(entity.getBody().getCode()).isEqualTo(50001);
        assertThat(entity.getBody().getMessage()).isEqualTo(ErrorCode.SYSTEM_ERROR.getMessage());
        assertThat(entity.getBody().getMessage())
                .doesNotContain("IllegalStateException", "db connection refused");
        assertThat(entity.getBody().getData()).isNull();
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
