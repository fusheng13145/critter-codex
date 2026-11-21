package com.just.critter.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

/**
 * 成员 A 域的可配置项（application.yaml 的 critter.* 段）。
 * 集中一处，避免 JWT/AES/文件目录等常量散落在各个类里。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "critter")
public class CritterProperties {

    private final Cache cache = new Cache();
    private final Security security = new Security();
    private final Register register = new Register();
    private final Login login = new Login();
    private final File file = new File();
    private final Export export = new Export();

    @Getter
    @Setter
    public static class Cache {
        /** memory | redis：本机无 Redis 默认 memory，接入 Redis 后改一行即切换 */
        private String mode = "memory";
    }

    @Getter
    @Setter
    public static class Security {
        private String jwtSecret;
        private long accessTokenSeconds = 7200;
        private long refreshTokenSeconds = 604800;
        private String aesKey;
        private String corsAllowedOrigins = "http://localhost:5173";
        /** 联调工具需要直接拿到刷新令牌时置 true；生产建议 false，只走 httpOnly Cookie */
        private boolean exposeRefreshToken = true;

        public List<String> corsOrigins() {
            return Arrays.stream(corsAllowedOrigins.split(","))
                    .map(String::trim)
                    .filter(item -> !item.isEmpty())
                    .toList();
        }
    }

    @Getter
    @Setter
    public static class Register {
        private String allowedEmailDomains = "just.edu.cn,stu.just.edu.cn";
        private boolean exposeVerificationCode = true;
        private long codeTtlSeconds = 300;

        public List<String> domains() {
            return Arrays.stream(allowedEmailDomains.split(","))
                    .map(String::trim)
                    .filter(item -> !item.isEmpty())
                    .toList();
        }
    }

    @Getter
    @Setter
    public static class Login {
        private int maxFailures = 5;
        private long lockSeconds = 600;
    }

    @Getter
    @Setter
    public static class File {
        private String storageDir = "data/files";
        private long maxSizeBytes = 5 * 1024 * 1024L;
    }

    /**
     * 统计导出（GET /stats/export）。
     * 超过 {@link #asyncThreshold} 行走异步任务：立即返回 taskId，前端轮询 /tasks/export/{taskId}。
     * 阈值做成配置项，测试可以调到 0 覆盖异步分支，不需要真造 5 万行数据。
     */
    @Getter
    @Setter
    public static class Export {
        private long asyncThreshold = 50000L;
        private String dir = "data/exports";
        /** 异步任务结果保留时长（秒），过期的下载地址自然失效 */
        private long taskTtlSeconds = 3600L;
    }
}
