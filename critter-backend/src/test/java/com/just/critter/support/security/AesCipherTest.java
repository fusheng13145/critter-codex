package com.just.critter.support.security;

import com.just.critter.config.CritterProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 手机号加密（NFR-04）：库里不能出现明文、同一明文两次加密结果不同（随机 IV）、
 * 展示层统一走脱敏。
 */
class AesCipherTest {

    private final AesCipher cipher = new AesCipher(properties());

    @Test
    @DisplayName("加密后可解密回原值，密文不含明文")
    void roundtrip() {
        String phone = "13812342210";

        String encrypted = cipher.encrypt(phone);

        assertThat(encrypted).isNotNull().isNotEqualTo(phone).doesNotContain(phone);
        assertThat(cipher.decrypt(encrypted)).isEqualTo(phone);
    }

    @Test
    @DisplayName("同一明文两次加密密文不同（随机 IV），杜绝可撞库的确定性密文")
    void cipher_text_differs_each_time() {
        String first = cipher.encrypt("13812342210");
        String second = cipher.encrypt("13812342210");

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo(cipher.decrypt(second));
    }

    @Test
    @DisplayName("空值与非密文按无值处理，单条脏数据不打断列表")
    void blank_and_garbage_are_safe() {
        assertThat(cipher.encrypt("  ")).isNull();
        assertThat(cipher.decrypt(null)).isNull();
        assertThat(cipher.decrypt("not-a-cipher-text")).isNull();
    }

    @Test
    @DisplayName("脱敏格式固定为 138****2210")
    void mask_format() {
        assertThat(AesCipher.mask("13812342210")).isEqualTo("138****2210");
        assertThat(AesCipher.mask(null)).isNull();
        assertThat(AesCipher.mask("123")).isEqualTo("***");
    }

    private CritterProperties properties() {
        CritterProperties properties = new CritterProperties();
        properties.getSecurity().setAesKey("qyWL2026CritterAESKey");
        return properties;
    }
}
