package com.just.critter.service.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.just.critter.controller.dto.LogDtos;
import com.just.critter.domain.model.SysLog;
import com.just.critter.mapper.SysLogMapper;
import com.just.critter.support.api.PageQuery;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 操作日志检索（FR-SYS-02 查询面，成员 A 主链路此前缺测）：
 * 分页入参归一、SysLog → LogItem 映射、total/pages 透传。
 */
@ExtendWith(MockitoExtension.class)
class LogServiceTest {

    @Mock
    private SysLogMapper logs;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysLog.class);
    }

    private SysLog log(long id, String action, String result) {
        SysLog row = new SysLog();
        row.setId(id);
        row.setActorId(1001L);
        row.setActorRole("STUDENT");
        row.setAction(action);
        row.setBizType("sys_user");
        row.setBizId("2023019001");
        row.setIp("127.0.0.1");
        row.setResult(result);
        row.setCreatedAt(LocalDateTime.of(2026, 11, 23, 12, 0));
        return row;
    }

    @Test
    @DisplayName("检索：条件查询映射为 LogItem，total/pages 透传给前端")
    void query_maps_rows_and_meta() {
        Page<SysLog> page = new Page<>(1, 20);
        page.setRecords(List.of(log(3, "LOGIN", "SUCCESS"), log(2, "FILE_AUDIT", "FAIL")));
        page.setTotal(2);
        page.setPages(1);
        when(logs.selectPage(any(), any())).thenReturn(page);

        LogService service = new LogService(logs);
        LogDtos.LogPage result = service.query(1001L, "LOGIN", null, null, null, null, null,
                new PageQuery(1, 20));

        assertThat(result.list()).hasSize(2);
        assertThat(result.list().get(0).action()).isEqualTo("LOGIN");
        assertThat(result.list().get(0).actorRole()).isEqualTo("STUDENT");
        assertThat(result.list().get(1).result()).isEqualTo("FAIL");
        assertThat(result.page().total()).isEqualTo(2);
        assertThat(result.page().pages()).isEqualTo(1);
    }

    @Test
    @DisplayName("空结果：返回空数组而不是 404（接口设计 §1 口径）")
    void query_returns_empty_list_when_no_hit() {
        Page<SysLog> page = new Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        when(logs.selectPage(any(), any())).thenReturn(page);

        LogService service = new LogService(logs);
        LogDtos.LogPage result = service.query(null, "NOT_EXIST", null, null, null, null, null,
                new PageQuery(3, 20));

        assertThat(result.list()).isEmpty();
        assertThat(result.page().total()).isZero();
    }
}
