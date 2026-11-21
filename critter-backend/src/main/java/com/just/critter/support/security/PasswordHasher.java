package com.just.critter.support.security;

import com.just.critter.config.CritterProperties;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 密码哈希：BCrypt 自带随机盐，库里只存哈希（数据库设计 §3.11）。
 * 只引 spring-security-crypto，不引完整 Spring Security，避免默认登录页与 CSRF 干扰。
 */
@Component
public class PasswordHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String hash) {
        if (rawPassword == null || hash == null || hash.isBlank()) {
            return false;
        }
        return encoder.matches(rawPassword, hash);
    }
}
