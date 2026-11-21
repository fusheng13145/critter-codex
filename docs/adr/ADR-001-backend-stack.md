# ADR-001 后端技术栈与工程口径

- 状态：已采纳
- 日期：2026-11-21
- 决策人：项目负责人（成员 B 执行）
- 关联文档：`05 概要设计说明书` §1、`03 接口设计说明书` §2/§3、`docs/一致性核对-代码与文档口径.md` §7

## 背景

仓库初始骨架由 Spring Initializr 生成，为 Spring Boot **4.0.10** + `mybatis-spring-boot-starter` 4.0.1，无 Redis；而设计文档要求 Spring Boot **3.x** + **MyBatis-Plus** + MySQL 8 + Redis。两者在 starter 命名、MyBatis-Plus 生态、课程验收口径上都不一致。

同期还确认了三项对外契约：包根沿用 `com.just.critter`、非法迁移同时返回中文 `message` 与 `data.allowed`、异常严格按 `03` §3 的 HTTP 列映射。

## 决策

| 项 | 决策 | 说明 |
|---|---|---|
| Spring Boot | **3.5.16** | 课程要求"SpringBoot 相关技术"，3.5.x 为当前 3.x 维护线，生态与 MyBatis-Plus 兼容稳定 |
| 持久层 | **MyBatis-Plus 3.5.18.1**（`mybatis-plus-spring-boot3-starter`） | 文档要求；单表 CRUD 与分页插件直接用，复杂 SQL 走 XML |
| 数据库 | **MySQL 8.4**（本机 `MySQL84` 服务），库名 `critter_codex`，`utf8mb4` | 迁移脚本放 `db/migration`，由 Flyway 执行 |
| 缓存 | **Redis**（`spring-boot-starter-data-redis`） | 一期用于字典缓存与令牌黑名单；未启动 Redis 不影响应用启动 |
| JDK | **21** | 与现有 `pom.xml` 和课程环境一致 |
| 包根 | **`com.just.critter`** | 代码现状；`com.qywl` 为早期草稿，文档已作废 |
| 统一响应 | `Result{code,message,data,timestamp,requestId}` | `message` 面向用户；`requestId` 由 `RequestIdFilter` 注入 |
| 异常 HTTP | 业务失败默认 200；401/403/404/409/500 按码映射 | 见 `ErrorHttpStatus` |
| 状态码族 | 30402 / 30505 / 30602 / 40108 | 一族一码，与 `03` §3 一致 |

## 备选方案与拒绝理由

1. **保留 Boot 4.0.10 + mybatis-starter**：省一次降级，但文档、课程口径与 MyBatis-Plus 能力都对不上；且在 Boot 4 上验证 MyBatis-Plus 兼容性成本更高。
2. **Boot 3.x 但继续用 mybatis-spring-boot-starter**：依赖更少，但放弃 MyBatis-Plus 的 `BaseMapper`、分页插件与 Lambda 查询，后续 37 张表的 CRUD 工作量显著增加。
3. **改包根为 `com.qywl`**：文档好看，但现有 26 个主代码文件与全部测试都要迁移，收益为零。

## 后果

- 正面：依赖与文档一致；MyBatis-Plus 可直接支撑 T1.2 起的档案 CRUD；HTTP 与响应契约有测试兜底（`ApiContractTest`）。
- 负面：Boot 4 专属 API 不可用（如 `spring-boot-starter-webmvc` 命名）；Flyway 版本随 Boot 3.5 管理，需同时引入 `flyway-mysql`。
- 验证：降级后全量 **63 例测试全绿**（2026-11-21），契约测试覆盖成功壳、`X-Request-Id` 透传、401/403/404/409/500 与 `data.allowed`。
- 遗留：Redis 目前只有依赖，无使用点；`Idempotency-Key` 随 T1.2 写接口落地。

## 复核方式

`critter-backend/pom.xml` 的 `spring-boot-starter-parent` 版本、`mybatis-plus-spring-boot3-starter` 坐标，以及 `docs/md/05 概要设计说明书` §1 技术栈行，三者必须一致；变更任一处的 PR 必须同步修改本 ADR。
