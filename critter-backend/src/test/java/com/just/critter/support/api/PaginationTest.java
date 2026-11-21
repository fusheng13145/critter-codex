package com.just.critter.support.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验收标准（接口设计说明书 §1 分页约定）：page 从 1 开始，size 默认 20、上限 100，
 * 分页数据固定包在 data.list 与 data.page{page,size,total,pages} 里。
 */
class PaginationTest {

    @Test
    @DisplayName("未传分页参数时默认第 1 页、每页 20 条")
    void defaults() {
        PageQuery query = new PageQuery();

        assertThat(query.getPage()).isEqualTo(1);
        assertThat(query.getSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("非法分页参数被夹到合法区间")
    void normalizes_out_of_range_values() {
        assertThat(new PageQuery(0, 0).normalized().getPage()).isEqualTo(1);
        assertThat(new PageQuery(0, 0).normalized().getSize()).isEqualTo(20);
        assertThat(new PageQuery(-3, 500).normalized().getPage()).isEqualTo(1);
        assertThat(new PageQuery(-3, 500).normalized().getSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("offset 由页码与每页条数推导")
    void computes_offset() {
        assertThat(new PageQuery(1, 20).offset()).isZero();
        assertThat(new PageQuery(3, 20).offset()).isEqualTo(40);
    }

    @Test
    @DisplayName("总页数按总数与每页条数向上取整")
    void computes_pages() {
        PageResult<String> result = PageResult.of(List.of("CAT-2026-031"), 1, 20, 137);

        assertThat(result.getPage().getTotal()).isEqualTo(137);
        assertThat(result.getPage().getPages()).isEqualTo(7);
        assertThat(result.getPage().getPage()).isEqualTo(1);
        assertThat(result.getPage().getSize()).isEqualTo(20);
        assertThat(result.getList()).hasSize(1);
    }

    @Test
    @DisplayName("空结果总页数为 0 而不是 1")
    void empty_total_has_zero_pages() {
        PageResult<String> result = PageResult.of(List.of(), 1, 20, 0);

        assertThat(result.getList()).isEmpty();
        assertThat(result.getPage().getPages()).isZero();
    }
}
