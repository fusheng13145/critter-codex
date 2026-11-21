package com.just.critter.config;

import com.just.critter.support.security.AuthInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 装配：CORS（前端 5173 直连时用）+ 鉴权拦截器白名单。
 * 匿名可访问的只有注册登录、找回密码、文件内容（公开照片）与健康检查；
 * 其余一律要求 Bearer 令牌。
 */
@Configuration
@EnableConfigurationProperties(CritterProperties.class)
public class WebConfig implements WebMvcConfigurer {

    private static final String[] ANONYMOUS = {
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/register/verify",
            "/api/v1/auth/refresh",
            "/api/v1/auth/password/**",
            "/api/v1/files/*/content",
            "/api/v1/ping"
    };

    private final AuthInterceptor authInterceptor;
    private final CritterProperties properties;

    public WebConfig(AuthInterceptor authInterceptor, CritterProperties properties) {
        this.authInterceptor = authInterceptor;
        this.properties = properties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(ANONYMOUS);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/v1/**")
                .allowedOrigins(properties.getSecurity().corsOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Request-Id")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
