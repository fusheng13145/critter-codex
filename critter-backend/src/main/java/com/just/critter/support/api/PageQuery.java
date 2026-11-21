package com.just.critter.support.api;

/**
 * 分页入参：page 从 1 开始，size 默认 20、上限 100。越界值统一夹紧，不报错。
 */
public class PageQuery {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final int page;
    private final int size;

    public PageQuery() {
        this(DEFAULT_PAGE, DEFAULT_SIZE);
    }

    public PageQuery(int page, int size) {
        this.page = page;
        this.size = size;
    }

    public PageQuery normalized() {
        int safePage = Math.max(page, DEFAULT_PAGE);
        int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return new PageQuery(safePage, safeSize);
    }

    public long offset() {
        PageQuery safe = normalized();
        return (long) (safe.page - 1) * safe.size;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }
}
