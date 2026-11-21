package com.just.critter.config;

import com.just.critter.support.security.AuthInterceptor;
import com.just.critter.support.security.OptionalAuthInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 装配：CORS（前端 5173 直连时用）+ 鉴权拦截器与白名单。
 *
 * <p>路径分三类：</p>
 * <ol>
 *   <li>{@link #ANONYMOUS} 匿名：注册登录、找回密码、公开附件内容、健康检查——
 *       不解析令牌，永远放行。</li>
 *   <li>{@link #OPTIONAL_AUTH} 可选鉴权：03 文档标注"公开"的档案/群体/物种/出现记录/
 *       点位/公告/项目/认养方案/留言——有合法令牌就解析身份，没有也放行。</li>
 *   <li>其余 /api/v1/** 一律要求有效 Bearer 令牌。</li>
 * </ol>
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
            // 禁用账号登不进来，申诉必须匿名可达（03 §6.1 第 14 项：公开）
            "/api/v1/users/me/appeals",
            "/api/v1/files/*/content",
            "/api/v1/ping"
    };

    /**
     * 03 §3–§8 中鉴权栏为"公开"的接口族（成员 B/C 的域）。
     * 只放行、不强制登录；带了令牌会解析成 {@code AuthContext}，供字段裁剪使用。
     *
     * <p>{@code /sightings} 在 03 里写的是"登录"，但 B 的控制器目前用 {@code X-User-Id}
     * 占位、暂未接入 AuthContext，先并入可选鉴权保证联调可用；等 B 切到令牌身份后
     * 再从这里移除即可（口径偏差已记入成员A交付说明）。</p>
     */
    private static final String[] OPTIONAL_AUTH = {
            "/api/v1/animals/**",
            "/api/v1/groups/**",
            "/api/v1/sightings/**",
            "/api/v1/species/**",
            "/api/v1/occurrences/**",
            "/api/v1/points/**",
            "/api/v1/notices/**",
            "/api/v1/projects/**",
            "/api/v1/plans/**",
            "/api/v1/comments/**"
    };

    private final AuthInterceptor authInterceptor;
    private final OptionalAuthInterceptor optionalAuthInterceptor;
    private final CritterProperties properties;

    public WebConfig(AuthInterceptor authInterceptor, CritterProperties properties) {
        this.authInterceptor = authInterceptor;
        this.optionalAuthInterceptor = new OptionalAuthInterceptor(authInterceptor);
        this.properties = properties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(optionalAuthInterceptor)
                .addPathPatterns(OPTIONAL_AUTH)
                .order(0);
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(ANONYMOUS)
                .excludePathPatterns(OPTIONAL_AUTH)
                .order(1);
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
