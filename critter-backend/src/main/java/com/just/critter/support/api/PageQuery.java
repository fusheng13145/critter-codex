package com.just.critter.support.api;

import java.util.Locale;

/**
 * 分页入参：page 从 1 开始，size 默认 20、上限 100；排序 {@code sort=field,asc|desc}。
 * 越界值统一夹紧，非法排序值不生效（不报错，回落默认排序）。
 */
public class PageQuery {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final String FIELD_PATTERN = "[A-Za-z][A-Za-z0-9_]*";

    private final int page;
    private final int size;
    private final String sort;

    public PageQuery() {
        this(DEFAULT_PAGE, DEFAULT_SIZE);
    }

    public PageQuery(int page, int size) {
        this(page, size, null);
    }

    public PageQuery(int page, int size, String sort) {
        this.page = page;
        this.size = size;
        this.sort = sort;
    }

    public PageQuery normalized() {
        int safePage = Math.max(page, DEFAULT_PAGE);
        int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return new PageQuery(safePage, safeSize, sort);
    }

    public long offset() {
        PageQuery safe = normalized();
        return (long) (safe.page - 1) * safe.size;
    }

    /** 排序字段；未传或非法时返回 null，由调用方使用默认排序。 */
    public String sortField() {
        String[] parts = parseSort();
        return parts == null ? null : parts[0];
    }

    /** 是否升序；未传方向时默认 asc。 */
    public boolean sortAscending() {
        String[] parts = parseSort();
        return parts == null || "asc".equals(parts[1]);
    }

    private String[] parseSort() {
        if (sort == null || sort.isBlank()) {
            return null;
        }
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        if (!field.matches(FIELD_PATTERN)) {
            return null;
        }
        String direction = parts.length > 1 ? parts[1].trim().toLowerCase(Locale.ROOT) : "asc";
        if (!"asc".equals(direction) && !"desc".equals(direction)) {
            return null;
        }
        return new String[]{field, direction};
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public String getSort() {
        return sort;
    }
}
