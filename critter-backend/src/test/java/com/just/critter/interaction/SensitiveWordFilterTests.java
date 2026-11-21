package com.just.critter.interaction;

import com.just.critter.interaction.service.SensitiveWordFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveWordFilterTests {

    private final SensitiveWordFilter filter = new SensitiveWordFilter();

    @Test
    @DisplayName("联系方式与营销词命中敏感规则")
    void detectsSolicitation() {
        assertThat(filter.isSensitive("加微信详聊")).isTrue();
        assertThat(filter.isSensitive("代开发票，价格优惠")).isTrue();
        assertThat(filter.isSensitive("详情见 https://spam.example.com")).isTrue();
    }

    @Test
    @DisplayName("正常咨询不误判，大小写不敏感")
    void keepsNormalContent() {
        assertThat(filter.isSensitive("请问这只猫最近还好吗？")).isFalse();
        assertThat(filter.isSensitive("QQ群")).isTrue();
        assertThat(filter.isSensitive("qq群")).isTrue();
    }
}
