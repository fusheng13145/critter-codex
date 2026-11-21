package com.just.critter.integration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 真库 IT 公共基线：所有集成测试统一使用独立的 critter_codex_it，
 * 并在首个 IT 类加载时清空重建，避免并行分支重编号后 Flyway 校验和冲突。
 */
abstract class MySqlIntegrationTestSupport {

    private static final String TEST_DATABASE = "critter_codex_it";

    static {
        if ("true".equals(System.getenv("CRITTER_MYSQL_IT"))) {
            resetTestDatabase();
        }
    }

    private static void resetTestDatabase() {
        if (!TEST_DATABASE.endsWith("_it")) {
            throw new IllegalStateException("拒绝重建非测试库：" + TEST_DATABASE);
        }
        String user = System.getenv().getOrDefault("CRITTER_MYSQL_USER", "root");
        String password = System.getenv().getOrDefault("CRITTER_MYSQL_PASSWORD", "");
        String url = "jdbc:mysql://127.0.0.1:3306/?useUnicode=true&characterEncoding=utf8"
                + "&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
        try (Connection connection = DriverManager.getConnection(url, user, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + TEST_DATABASE);
        } catch (SQLException ex) {
            throw new IllegalStateException("重建 MySQL 测试库失败", ex);
        }
    }
}
