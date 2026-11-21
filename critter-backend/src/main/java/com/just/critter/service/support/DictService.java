package com.just.critter.service.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.just.critter.controller.dto.DictDtos;
import com.just.critter.domain.model.SysDict;
import com.just.critter.mapper.SysDictMapper;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.exception.FieldErrorItem;
import com.just.critter.support.log.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 字典服务：表单下拉的唯一来源（FR-SYS-03 的基础部分）。
 * 后台改一条字典后前端重新取 /dicts 立即生效，不做进程内缓存，
 * 避免"改了字典但页面还是旧值"这类演示事故。
 */
@Service
public class DictService {

    private static final Pattern TYPE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,31}$");

    private final SysDictMapper dictMapper;
    private final AuditLogService auditLog;

    public DictService(SysDictMapper dictMapper, AuditLogService auditLog) {
        this.dictMapper = dictMapper;
        this.auditLog = auditLog;
    }

    /** 批量取字典，只返回启用项。type 非法（空、小写、含中文）返回 30303。 */
    public DictDtos.DictBundle getDicts(List<String> types) {
        if (types == null || types.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "请指定字典类型",
                    List.of(new FieldErrorItem("type", "至少传一个字典类型")));
        }
        List<String> normalized = new ArrayList<>();
        for (String raw : types) {
            String type = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
            if (!TYPE_PATTERN.matcher(type).matches()) {
                throw new BusinessException(ErrorCode.ENUM_INVALID.getCode(), "字典类型非法：" + raw);
            }
            normalized.add(type);
        }

        List<SysDict> rows = dictMapper.selectList(Wrappers.<SysDict>lambdaQuery()
                .in(SysDict::getDictType, normalized)
                .eq(SysDict::getEnabled, true)
                .orderByAsc(SysDict::getDictType)
                .orderByAsc(SysDict::getSortNo)
                .orderByAsc(SysDict::getId));

        Map<String, List<DictDtos.DictItem>> grouped = new LinkedHashMap<>();
        normalized.forEach(type -> grouped.put(type, new ArrayList<>()));
        rows.forEach(row -> grouped.computeIfAbsent(row.getDictType(), key -> new ArrayList<>())
                .add(new DictDtos.DictItem(row.getId(), row.getDictKey(), row.getDictLabel(),
                        row.getSortNo() == null ? 0 : row.getSortNo(),
                        Boolean.TRUE.equals(row.getEnabled()))));

        return new DictDtos.DictBundle(grouped, normalized);
    }

    /** 后台维护用：含停用项。 */
    public List<DictDtos.DictView> adminList(String type) {
        List<SysDict> rows = dictMapper.selectList(Wrappers.<SysDict>lambdaQuery()
                .eq(type != null && !type.isBlank(), SysDict::getDictType, type)
                .orderByAsc(SysDict::getDictType)
                .orderByAsc(SysDict::getSortNo)
                .orderByAsc(SysDict::getId));
        return rows.stream().map(this::toView).toList();
    }

    @Transactional
    public DictDtos.DictView create(DictDtos.DictCreateRequest request) {
        String type = request.type().trim().toUpperCase(Locale.ROOT);
        String key = request.key().trim();
        if (!TYPE_PATTERN.matcher(type).matches()) {
            throw new BusinessException(ErrorCode.ENUM_INVALID.getCode(), "字典类型需为大写字母、数字或下划线");
        }
        long exists = dictMapper.selectCount(Wrappers.<SysDict>lambdaQuery()
                .eq(SysDict::getDictType, type)
                .eq(SysDict::getDictKey, key));
        if (exists > 0) {
            throw new BusinessException(ErrorCode.CODE_DUPLICATED.getCode(), "该字典键已存在");
        }
        SysDict row = new SysDict();
        row.setDictType(type);
        row.setDictKey(key);
        row.setDictLabel(request.label().trim());
        row.setSortNo(request.sortNo() == null ? 99 : request.sortNo());
        row.setEnabled(true);
        dictMapper.insert(row);
        auditLog.success("DICT_CREATE", "sys_dict", String.valueOf(row.getId()), null,
                Map.of("type", type, "key", key, "label", row.getDictLabel()));
        return toView(row);
    }

    @Transactional
    public DictDtos.DictView update(long id, DictDtos.DictUpdateRequest request) {
        SysDict before = dictMapper.selectById(id);
        if (before == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        SysDict patch = new SysDict();
        patch.setId(id);
        patch.setDictLabel(request.label() == null ? before.getDictLabel() : request.label().trim());
        patch.setSortNo(request.sortNo() == null ? before.getSortNo() : request.sortNo());
        patch.setEnabled(request.enabled() == null ? before.getEnabled() : request.enabled());
        dictMapper.updateById(patch);
        auditLog.success("DICT_UPDATE", "sys_dict", String.valueOf(id),
                Map.of("label", before.getDictLabel(), "enabled", before.getEnabled()),
                Map.of("label", patch.getDictLabel(), "enabled", patch.getEnabled()));
        return toView(dictMapper.selectById(id));
    }

    private DictDtos.DictView toView(SysDict row) {
        return new DictDtos.DictView(row.getId(), row.getDictType(), row.getDictKey(), row.getDictLabel(),
                row.getSortNo() == null ? 0 : row.getSortNo(), Boolean.TRUE.equals(row.getEnabled()));
    }
}
