package com.just.critter.controller;

import com.just.critter.controller.dto.AuthDtos;
import com.just.critter.service.auth.AuthService;
import com.just.critter.support.api.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 认证接口（接口设计 §7.1）。刷新令牌走 httpOnly Cookie，访问令牌由前端放内存，
 * 这样 XSS 拿不到刷新令牌；响应体同时回传 refreshToken 只为联调工具方便，
 * 生产环境把 critter.security.expose-refresh-token 置 false 即可。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String REFRESH_COOKIE = "critter_rt";

    private final AuthService authService;
    private final com.just.critter.config.CritterProperties properties;

    public AuthController(AuthService authService, com.just.critter.config.CritterProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    @PostMapping("/register")
    public Result<AuthDtos.RegisterResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return Result.ok(authService.register(request));
    }

    @PostMapping("/register/verify")
    public Result<AuthDtos.VerifyResponse> verify(@Valid @RequestBody AuthDtos.VerifyRequest request) {
        return Result.ok(authService.verify(request));
    }

    @PostMapping("/login")
    public ResponseEntity<Result<AuthDtos.LoginResponse>> login(@Valid @RequestBody AuthDtos.LoginRequest request,
                                                                HttpServletRequest servletRequest) {
        AuthDtos.LoginResponse response = authService.login(request, clientIp(servletRequest));
        AuthDtos.LoginResponse body = properties.getSecurity().isExposeRefreshToken() ? response
                : new AuthDtos.LoginResponse(response.accessToken(), null, response.expiresIn(), response.user());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(response.refreshToken()).toString())
                .body(Result.ok(body));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Result<AuthDtos.TokenResponse>> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String cookieToken) {
        AuthService.Refreshed refreshed = authService.refresh(cookieToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(refreshed.refreshToken()).toString())
                .body(Result.ok(refreshed.body()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Result<Void>> logout(
            @org.springframework.web.bind.annotation.RequestHeader(value = HttpHeaders.AUTHORIZATION,
                    required = false) String authorization) {
        authService.logout(authorization);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie().toString())
                .body(Result.ok());
    }

    private ResponseCookie refreshCookie(String token) {
        return ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true)
                .path("/api/v1/auth")
                .maxAge(Duration.ofSeconds(properties.getSecurity().getRefreshTokenSeconds()))
                .sameSite("Lax")
                .build();
    }

    private ResponseCookie clearCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .path("/api/v1/auth")
                .maxAge(0)
                .sameSite("Lax")
                .build();
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        return request.getRemoteAddr();
    }
}
