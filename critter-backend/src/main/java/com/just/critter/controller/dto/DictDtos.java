package com.just.critter.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/** 字典出入参：GET /dicts 批量取，POST/PUT 由后台维护。 */
public final class DictDtos {

    private DictDtos() {
    }

    public record DictItem(long id, String key, String label, int sortNo, boolean enabled) {
    }

    /** data.dicts{ TYPE: [ {key,label} ] }，与接口设计 §1.2 第 11 项一致。 */
    public record DictBundle(Map<String, List<DictItem>> dicts, List<String> types) {
    }

    public record DictCreateRequest(
            @NotBlank(message = "请选择字典类型") String type,
            @NotBlank(message = "请填写字典键") @Size(max = 32, message = "键过长") String key,
            @NotBlank(message = "请填写显示名") @Size(max = 64, message = "显示名过长") String label,
            Integer sortNo) {
    }

    public record DictUpdateRequest(
            @Size(max = 64, message = "显示名过长") String label,
            Integer sortNo,
            Boolean enabled) {
    }

    public record DictView(long id, String type, String key, String label, int sortNo, boolean enabled) {
    }
}
