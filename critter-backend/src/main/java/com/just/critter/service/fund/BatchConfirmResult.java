package com.just.critter.service.fund;

import java.util.List;

/**
 * 批量核对结果：success 为成功笔数，failed 逐条回码，前端直接展示。
 */
public record BatchConfirmResult(int success, List<BatchFailure> failed) {
}
