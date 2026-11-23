package com.just.critter.service.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.just.critter.controller.dto.DictDtos;
import com.just.critter.domain.model.SysDict;
import com.just.critter.mapper.SysDictMapper;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.log.AuditLogService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 字典服务（FR-SYS-03 基础部分，成员 A 主链路此前缺测）：
 * type 非法 30303、type+key 重复 30411、批量取按类型分组、后台启停留痕。
 */
@ExtendWith(MockitoExtension.class)
class DictServiceTest {

    @Mock
    private SysDictMapper dicts;
    @Mock
    private AuditLogService audit;

    private DictService service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysDict.class);
    }

    private SysDict dict(String type, String key, String label, boolean enabled) {
        SysDict row = new SysDict();
        row.setId((long) (type.hashCode() & 0xffff));
        row.setDictType(type);
        row.setDictKey(key);
        row.setDictLabel(label);
        row.setSortNo(1);
        row.setEnabled(enabled);
        return row;
    }

    @Test
    @DisplayName("type 为空：30301 并要求至少传一个")
    void get_dicts_requires_type() {
        service = new DictService(dicts, audit);

        assertThatThrownBy(() -> service.getDicts(List.of()))
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.PARAM_INVALID.getCode());
    }

    @Test
    @DisplayName("type 非法（含中文/含空格）：30303；小写会归一为大写不算非法")
    void get_dicts_rejects_illegal_type() {
        service = new DictService(dicts, audit);

        assertThatThrownBy(() -> service.getDicts(List.of("颜色CAT")))
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.ENUM_INVALID.getCode());
        assertThatThrownBy(() -> service.getDicts(List.of("HAS SPACE")))
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.ENUM_INVALID.getCode());
    }

    @Test
    @DisplayName("批量取：按类型分组（启用过滤由 SQL 承担，mock 只给启用行）")
    void get_dicts_groups_by_type() {
        service = new DictService(dicts, audit);
        when(dicts.selectList(any())).thenReturn(List.of(
                dict("ANIMAL_COLOR", "SANHUA", "三花", true),
                dict("ANIMAL_COLOR", "JUBAI", "橘白", true),
                dict("ANIMAL_STATUS", "ON_CAMPUS", "在校园活动", true)));

        DictDtos.DictBundle bundle = service.getDicts(List.of("ANIMAL_COLOR", "ANIMAL_STATUS"));

        assertThat(bundle.types()).containsExactly("ANIMAL_COLOR", "ANIMAL_STATUS");
        assertThat(bundle.dicts().get("ANIMAL_COLOR")).extracting(DictDtos.DictItem::label)
                .containsExactly("三花", "橘白");
        assertThat(bundle.dicts().get("ANIMAL_STATUS")).hasSize(1);
    }

    @Test
    @DisplayName("新增：type 归一为大写，未传排序默认 99")
    void create_normalizes_type_and_defaults_sort() {
        service = new DictService(dicts, audit);
        when(dicts.selectCount(any())).thenReturn(0L);
        when(dicts.insert(any(SysDict.class))).thenAnswer(inv -> {
            ((SysDict) inv.getArgument(0)).setId(500L);
            return 1;
        });

        DictDtos.DictView view = service.create(
                new DictDtos.DictCreateRequest("animal_color", "smoke", "烟灰色", null));

        ArgumentCaptor<SysDict> captor = ArgumentCaptor.forClass(SysDict.class);
        verify(dicts).insert(captor.capture());
        assertThat(captor.getValue().getDictType()).isEqualTo("ANIMAL_COLOR");
        assertThat(captor.getValue().getSortNo()).isEqualTo(99);
        assertThat(view.label()).isEqualTo("烟灰色");
        assertThat(view.id()).isEqualTo(500L);
    }

    @Test
    @DisplayName("新增重复键：30411")
    void create_rejects_duplicate_key() {
        service = new DictService(dicts, audit);
        when(dicts.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.create(
                new DictDtos.DictCreateRequest("ANIMAL_COLOR", "SMOKE", "再一个烟灰色", 9)))
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.CODE_DUPLICATED.getCode());
    }

    @Test
    @DisplayName("停用：enabled 落库且写 DICT_UPDATE 审计")
    void update_toggles_enabled_and_audits() {
        service = new DictService(dicts, audit);
        SysDict existing = dict("ANIMAL_COLOR", "SMOKE", "烟灰色", true);
        SysDict updated = dict("ANIMAL_COLOR", "SMOKE", "烟灰色", false);
        when(dicts.selectById(42L)).thenReturn(existing, updated);
        when(dicts.updateById(any(SysDict.class))).thenReturn(1);

        DictDtos.DictView view = service.update(42L, new DictDtos.DictUpdateRequest(null, null, false));

        assertThat(view.enabled()).isFalse();
        verify(audit).success(eq("DICT_UPDATE"), any(), any(), any(), any());
    }
}
