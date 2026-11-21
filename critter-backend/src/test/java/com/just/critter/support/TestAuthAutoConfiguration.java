package com.just.critter.support;

import com.just.critter.support.security.JwtService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 仅测试类路径生效：为 @SpringBootTest 的 MockMvc 请求注入合法访问令牌。
 * 生产环境不会加载本类；安全边界测试可在请求中显式覆盖 Authorization 头。
 */
@AutoConfiguration
public class TestAuthAutoConfiguration {

    @Bean
    MockMvcBuilderCustomizer testAuthCustomizer(JwtService jwtService) {
        String token = jwtService.issueAccess(
                1001L,
                "TEST-USER",
                "ANIMAL_ADMIN",
                Set.of("ANIMAL_ADMIN", "SYS_ADMIN", "SYSTEM_ADMIN", "VOLUNTEER", "ADOPTER"),
                "测试环境").token();
        return builder -> builder.defaultRequest(get("/api/v1/ping")
                .header("Authorization", "Bearer " + token));
    }
}
