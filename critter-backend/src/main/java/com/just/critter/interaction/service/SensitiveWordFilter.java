package com.just.critter.interaction.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 轻量敏感词过滤：只拦截引流、营销与垃圾信息，不做人身攻击词库。
 * 采用包含匹配，英文按小写归一，后续可改为配置表而不影响调用方。
 */
@Component
public class SensitiveWordFilter {

    private static final List<String> DEFAULT_WORDS = List.of(
            "加微信", "加qq", "qq群", "代开发票", "办证", "博彩", "刷单",
            "贷款", "返利", "兼职日结", "http://", "https://", "www.");

    private final List<String> words;

    public SensitiveWordFilter() {
        this(DEFAULT_WORDS);
    }

    SensitiveWordFilter(List<String> words) {
        this.words = List.copyOf(words);
    }

    public boolean isSensitive(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String normalized = content.toLowerCase(Locale.ROOT);
        return words.stream().anyMatch(normalized::contains);
    }
}
