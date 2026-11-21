# 成员 C · D3 验收与 TDD 记录

## 当前实施顺序

1. 认养方案列表：先完成无争议的最小垂直切片。
2. 认养申请、审核、缴费：缴费契约确认后实施。
3. 就诊、消息、捐赠登记：消息生产契约确认后实施联动。
4. 到期、TNR 与后续扩展：主链路通过后实施。

## 第 1 轮：认养方案列表

### 验收标准

- `GET /api/v1/plans` 返回 HTTP 200。
- 响应采用统一结构：`code`、`message`、`data`、`requestId`、`timestamp`。
- 成功时 `code = 0`、`message = "ok"`，`data` 为数组；没有方案时返回空数组。
- 每个方案包含 `planId`、`planName`、`period`、`amount`、`benefits`。
- `period` 仅使用需求已定义的 `MONTH` 或 `QUARTER`，`amount` 为整数分。
- 服务层只返回启用方案，停用方案不得进入响应。
- 本轮不写入正式金额档位，不增加年度方案，不接数据库；测试中的金额仅是测试夹具。

### TDD 执行记录

- Red：先编写控制器契约测试和服务层启用过滤测试，确认当前实现缺失时测试失败。
- Green：仅实现使上述测试通过的控制器、服务、领域模型与仓储。
- Refactor：检查命名、分层与响应结构，运行相关测试和完整后端测试套件。

### 第 1 轮执行结果（2026-11-21）

- Red 证据：测试编译失败，共 25 个错误，均为 `adopt/controller`、`adopt/domain`、`adopt/repository`、`adopt/service` 类型尚不存在；不是断言失败冒充 RED。
- Green 证据：相关测试 `AdoptionPlanControllerTests`、`AdoptionPlanServiceTests` 共 2 例通过。
- 重构：服务层返回领域对象，控制器负责映射响应 DTO，消除 service 对 controller 的反向依赖；重构后相关测试仍 2/2 通过。
- 全量回归：后端完整测试套件 65 例通过，0 失败、0 错误、0 跳过。
- 真库结构测试：显式开启 `CRITTER_MYSQL_IT=true` 后运行 5 例，因测试固定使用 `root/1234` 而本机 MySQL 返回 `Access denied for user 'root'@'localhost'`，5 例均在 Spring 上下文初始化阶段阻塞；本次未修改 B 的测试凭据。
- 本机运行前置：Maven 必须使用 JDK 21；当前机器可用路径为 `D:\jdk21\jdk-21.0.6+7`，默认 `JAVA_HOME` 的 JDK 17 无法编译本项目。

### 第 1 轮完成边界

- 已完成：公开 `GET /api/v1/plans` 的 v1.1 响应壳、启用过滤、领域模型与空仓储装配。
- 未完成：尚未接 `adoption_plan` 数据表，尚无正式方案种子；这不是完整“方案配置”功能。
- 下一轮：认养申请领域规则。开工前仍需确认动物状态查询方式、并发锁实现和待审核期间是否允许同动物重复申请。
- 仍阻塞：缴费的“学生提交凭证”契约、消息生产通道、FR-M-02 手术记录表与接口未定。

## 第 2 轮：公开方案与认养申请

### 验收标准

- `GET /api/v1/plans` 即使携带无效令牌也必须匿名可读，不能因令牌解析失败返回 401。
- `POST /api/v1/adoptions` 仅从 Bearer 鉴权上下文获取用户，不信任 `X-User-Id`。
- 必填校验：`animalCode`、`planId`、`promiseAccepted=true`、`contact`；任一缺失返回 30301。
- 方案必须存在且启用，否则返回 30410。
- 动物查询必须在事务内加 `FOR UPDATE` 行锁，仅允许 `ON_CAMPUS`、`RELEASED`，否则返回 30406。
- 动物已有 `ACTIVE` 认养时返回 30404，并携带 `activeAdoptionNo`。
- 本人已有两条 `ACTIVE` 认养时返回 30405。
- 成功时生成 `ADyyyyMMddNNN` 单号，方案金额和周期快照入单，状态为 `PENDING_AUDIT`。
- 本轮不包含审核、缴费、消息通知和前端页面；`remark` 仅补数据库列，不等于完整备注业务。

### TDD 执行记录（2026-11-21）

- Red：匿名方案测试实际收到 HTTP 401；认养申请测试因 16 个目标类型缺失而编译失败。
- Green：认养方案与申请相关测试 10/10 通过。
- 重构：Mapper 归入 `com.just.critter.mapper`，实体归入 `domain/model`，服务返回领域对象，控制器负责响应映射。
- 完整套件：145/145 通过，0 失败、0 错误、0 跳过。
- 真库测试：独立库 `critter_codex_it` 上 5/5 通过；凭据通过 `CRITTER_MYSQL_USER`、`CRITTER_MYSQL_PASSWORD` 注入，不写入仓库。

### 本轮顺带修复的合并阻塞

- `pom.xml` 重复声明 MyBatis-Plus JSQLParser 依赖。
- `AppConfig` 与 `MybatisPlusConfig` 重复注册 MyBatis-Plus 拦截器。
- 两个 `V2` 迁移并存；身份种子顺延为 `V5`。
- `V3` 在补列前插入 `sys_user_role.status`；改为幂等补列后再插种子。
- 既有接口测试未适配 Bearer 鉴权；新增测试类路径专用 MockMvc 令牌，不影响生产。

### 未覆盖与下一步

- `AdoptionMapper`、`AdoptionAnimalMapper` 尚无真库读写集成测试，当前只验证了 SQL 迁移可执行；下一轮先补该集成测试。
- 下一功能为认养审核 `PUT /adoptions/{no}/audit`，状态从 `PENDING_AUDIT` 进入 `PENDING_PAYMENT`。
- 缴费仍需先确认“学生提交凭证”接口与两态模型。

### 与远端 D1 第二批整合（2026-11-21）

- 推送前拉取到远端 4 个新提交（身份与看板第二批、V5 参考数据重命名、V6 字典归一、V7 角色权限与申诉）。
- 变基冲突采用远端单一装配方案：MyBatis-Plus 拦截器只保留 `AppConfig`，删除 `MybatisPlusConfig`。
- 因两端同时占用版本号，本域迁移顺延：身份种子 `V5` → `V8`，认养备注 `V6` → `V9`；当前迁移序列为 V1、V3、V4、V5 参考数据、V6 字典、V7 角色权限、V8 身份、V9 认养备注。
- 真库集成测试增加启动前重建专用库 `critter_codex_it`，避免并行迁移重编号造成旧库校验和失效；业务表基线更新为 39 张 + `seq_no`。
- 整合后单元测试 169/169 通过；真库迁移测试 5/5 通过。日志中 V6 的字典去重告警来自幂等清理路径，迁移最终成功。
- 尚未覆盖：认养 Mapper 真库读写/行锁集成测试、前端 D3 页面、审核与缴费主链路。

