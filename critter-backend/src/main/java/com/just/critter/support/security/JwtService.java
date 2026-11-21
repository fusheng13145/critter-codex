package com.just.critter.support.security;

import com.just.critter.config.CritterProperties;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 双令牌签发与解析（接口设计 §4）：访问令牌 2 小时、刷新令牌 7 天。
 * 令牌里只放 sub(学号)、roles、primaryRole、dept 与 jti，不放手机号等联系方式。
 */
@Component
public class JwtService {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final long accessSeconds;
    private final long refreshSeconds;

    public JwtService(CritterProperties properties) {
        String secret = properties.getSecurity().getJwtSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("critter.security.jwt-secret 至少需要 32 字节");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessSeconds = properties.getSecurity().getAccessTokenSeconds();
        this.refreshSeconds = properties.getSecurity().getRefreshTokenSeconds();
    }

    public Issued issueAccess(long userId, String userNo, String primaryRole, Set<String> roles, String dept) {
        return issue(userId, userNo, primaryRole, roles, dept, accessSeconds, TYPE_ACCESS);
    }

    public Issued issueRefresh(long userId, String userNo, String primaryRole, Set<String> roles, String dept) {
        return issue(userId, userNo, primaryRole, roles, dept, refreshSeconds, TYPE_REFRESH);
    }

    private Issued issue(long userId, String userNo, String primaryRole, Set<String> roles, String dept,
                         long seconds, String type) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(seconds);
        String jwtId = UUID.randomUUID().toString().replace("-", "");
        String token = Jwts.builder()
                .subject(userNo)
                .id(jwtId)
                .claim("uid", userId)
                .claim("primaryRole", primaryRole)
                .claim("roles", List.copyOf(roles))
                .claim("dept", dept)
                .claim("typ", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new Issued(token, jwtId, expiresAt, seconds);
    }

    /** 解析并校验签名与有效期；任何异常统一收敛为 30102（HTTP 401）。 */
    public Payload parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Set<String> roles = new LinkedHashSet<>();
            Object rawRoles = claims.get("roles");
            if (rawRoles instanceof List<?> list) {
                list.forEach(item -> roles.add(String.valueOf(item)));
            }
            Number uid = claims.get("uid", Number.class);
            return new Payload(
                    uid == null ? 0L : uid.longValue(),
                    claims.getSubject(),
                    claims.get("primaryRole", String.class),
                    roles,
                    claims.get("dept", String.class),
                    claims.getId(),
                    claims.getIssuedAt().toInstant(),
                    claims.getExpiration().toInstant(),
                    claims.get("typ", String.class));
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
    }

    public record Issued(String token, String jwtId, Instant expiresAt, long expiresInSeconds) {
    }

    public record Payload(long userId, String userNo, String primaryRole, Set<String> roles,
                          String dept, String jwtId, Instant issuedAt, Instant expiresAt, String type) {

        public boolean isAccess() {
            return TYPE_ACCESS.equals(type);
        }

        public boolean isRefresh() {
            return TYPE_REFRESH.equals(type);
        }
    }
}
