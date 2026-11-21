package com.just.critter.controller.fund;

import java.util.List;

/**
 * 批量核对请求体，遵循接口文档 §8“PUT /xxx/batch + ids”。
 */
public record BatchDonationRequest(List<String> ids) {
}
