package com.just.critter.support.security;

import com.just.critter.config.CritterProperties;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 双令牌契约（接口设计 §4）：载荷只放必要声明、类型不可混用、篡改即 30102。
 */
class JwtServiceTest {

    private final JwtService jwtService = new JwtService(properties());

    @Test
    @DisplayName("访问令牌携带学号、角色、院系与 jti，且不带联系方式")
    void access_token_carries_expected_claims() {
        JwtService.Issued issued = jwtService.issueAccess(7L, "2023019001", "STUDENT",
                Set.of("STUDENT", "ADOPTER"), "计算机学院");

        JwtService.Payload payload = jwtService.parse(issued.token());

        assertThat(payload.userId()).isEqualTo(7L);
        assertThat(payload.userNo()).isEqualTo("2023019001");
        assertThat(payload.primaryRole()).isEqualTo("STUDENT");
        assertThat(payload.roles()).containsExactlyInAnyOrder("STUDENT", "ADOPTER");
        assertThat(payload.dept()).isEqualTo("计算机学院");
        assertThat(payload.jwtId()).isEqualTo(issued.jwtId());
        assertThat(payload.isAccess()).isTrue();
        assertThat(payload.isRefresh()).isFalse();
    }

    @Test
    @DisplayName("刷新令牌的 typ 为 refresh，不能当访问令牌用")
    void refresh_token_is_typed() {
        JwtService.Issued issued = jwtService.issueRefresh(7L, "2023019001", "STUDENT",
                Set.of("STUDENT"), "计算机学院");

        JwtService.Payload payload = jwtService.parse(issued.token());

        assertThat(payload.isRefresh()).isTrue();
        assertThat(payload.isAccess()).isFalse();
        assertThat(payload.expiresAt()).isAfter(Instant.now().plusSeconds(3600));
    }

    @Test
    @DisplayName("签名被篡改返回 30102，不泄漏解析细节")
    void tampered_token_is_rejected() {
        JwtService.Issued issued = jwtService.issueAccess(7L, "2023019001", "STUDENT",
                Set.of("STUDENT"), "计算机学院");
        String tampered = issued.token().substring(0, issued.token().length() - 2) + "xx";

        assertThatThrownBy(() -> jwtService.parse(tampered))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.TOKEN_INVALID.getCode());
    }

    @Test
    @DisplayName("密钥不足 32 字节直接启动失败，避免弱签名")
    void weak_secret_is_refused() {
        CritterProperties properties = properties();
        properties.getSecurity().setJwtSecret("too-short");

        assertThatThrownBy(() -> new JwtService(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt-secret");
    }

    private CritterProperties properties() {
        CritterProperties properties = new CritterProperties();
        properties.getSecurity().setJwtSecret("qingyan-wanling-ce-campus-critter-codex-2026-secret-key");
        return properties;
    }
}
