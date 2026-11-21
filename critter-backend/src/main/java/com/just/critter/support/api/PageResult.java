package com.just.critter.support.api;

import java.util.List;

/**
 * 分页出参：固定包成 data.list + data.page{page,size,total,pages}，前端分页组件可直接绑定。
 */
public class PageResult<T> {

    private final List<T> list;
    private final PageMeta page;

    private PageResult(List<T> list, PageMeta page) {
        this.list = list;
        this.page = page;
    }

    public static <T> PageResult<T> of(List<T> list, int page, int size, long total) {
        long pages = total == 0 ? 0 : (total + size - 1) / size;
        return new PageResult<>(list, new PageMeta(page, size, total, pages));
    }

    public List<T> getList() {
        return list;
    }

    public PageMeta getPage() {
        return page;
    }

    public static class PageMeta {

        private final int page;
        private final int size;
        private final long total;
        private final long pages;

        public PageMeta(int page, int size, long total, long pages) {
            this.page = page;
            this.size = size;
            this.total = total;
            this.pages = pages;
        }

        public int getPage() {
            return page;
        }

        public int getSize() {
            return size;
        }

        public long getTotal() {
            return total;
        }

        public long getPages() {
            return pages;
        }
    }
}
