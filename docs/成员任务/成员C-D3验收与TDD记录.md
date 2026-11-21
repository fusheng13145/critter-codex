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
- 因两端同时占用版本号，本域迁移顺延：身份种子最终为 `V11`，认养备注为 `V12`；当前迁移序列为 V1、V3、V4、V5 参考数据、V6 字典归一、V7 角色权限、V8 上报字段、V9 字典归一、V10 字典规范类型、V11 身份种子、V12 认养备注。
- 第二次整合远端 D2 上报/工单 4 个提交时，保留远端 `isPublicGet` 与本地 `@PublicEndpoint` 两套公开访问判定：前者覆盖公开只读，后者覆盖方案列表等公开方法。
- 真库集成测试增加 `MySqlIntegrationTestSupport`：所有 IT 统一连接独立库 `critter_codex_it`、经环境变量读取凭据，并在首个 IT 类加载时清空重建，避免并行迁移重编号造成旧库校验和失效；业务表基线更新为 39 张 + `seq_no`。
- 全量 IT 基线的 6 个旧测试类此前硬编码开发库与 `root/1234`，已统一改为独立测试库和 `CRITTER_MYSQL_USER`/`CRITTER_MYSQL_PASSWORD`，不再污染开发库。
- 整合后单元测试 205/205 通过；全部真库 IT 23/23 通过（含 `MySqlSchemaIT` 5/5、`SeedReferenceDataIT` 4/4、上报 4/4、工单 2/2）。日志中字典去重告警来自幂等清理路径，迁移最终成功。
- 尚未覆盖：认养 Mapper 真库读写/行锁集成测试、前端 D3 页面、审核与缴费主链路。

### 第三轮：认养审核（2026-11-21）

**验收标准**

- `PUT /api/v1/adoptions/{no}/audit` 仅 `ANIMAL_ADMIN`、`SYS_ADMIN`、`SYSTEM_ADMIN` 可调用，审核人从 Bearer 上下文读取。
- 只允许 `PENDING_AUDIT` 单进入审核；同一单被他人处理后返回 30407。
- `APPROVE` 后状态为 `PENDING_PAYMENT`，返回 `payDeadline` 为审核时刻加 7 天，带 `+08:00` 时区；绝不在此步写 `ACTIVE`。
- 审核通过前对动物行加锁，并检查同一动物是否已有其他 `PENDING_PAYMENT`/`ACTIVE` 单；有则返回 30407 与原单号。
- `REJECT` 必须填写去空格后不少于 5 字的理由，返回状态 `REJECTED`，`payDeadline` 为空。
- 调整金额时必须给出不少于 5 字的说明；金额不得小于等于 0；单不存在返回 30410。

**RED**

- 新增 7 个服务测试和 2 个控制器测试，首次运行出现 7 个编译错误：`AdoptionAuditService`、`AuditedAdoption`、`AdoptionAuditCommand`、`AdoptionDecision` 均不存在。

**GREEN 与重构**

- 新增 `AdoptionDecision`、`AdoptionAuditCommand`、`AuditedAdoption`。
- `AdoptionRepository` 增加 `findByNoForUpdate`、`findBlockingAdoptionNo`、`updateAudit`；Mapper 使用 `SELECT ... FOR UPDATE` 与带 `status='PENDING_AUDIT'` 条件的更新，更新 0 行仍按 30407 处理。
- `AdoptionAuditService` 先做命令校验，再锁单、锁动物、查占用、条件更新；通过后计算 7 天缴费期限。
- `AdoptionController` 增加审核路由与角色注解；请求 DTO 使用枚举 `APPROVE/REJECT`。
- 新增 `AdoptionAuditIT`：真实 MySQL 落审核通过/驳回、审核人、金额、说明与状态。

**测试结果**

- 审核相关 9/9 通过。
- 完整单元测试 234/234 通过，0 失败、0 错误、0 跳过。
- 全部真库 IT 29/29 通过，包含 `AdoptionAuditIT` 2/2 与远端新增 `ProjectIT` 4/4。
- 远端 T2.4 合并后，`ProjectIT` 也从开发库 `root/1234` 切到 `critter_codex_it` 与环境变量，并接入统一测试库重建基线。
- 当前未覆盖：缴费入账（含凭证）、我的认养/详情/终止/证书、前端审核页与联调。

## 第四轮：认养查询、缴费入账、终止、证书与前端审核页（2026-11-21）

### 验收标准

- 管理员 `GET /adoptions` 支持 `status/page/size`，返回 `data.list + data.page`，联系方式脱敏；非管理员返回 20003。
- `GET /adoptions/mine` 只按 Bearer 当前用户查询，可按状态筛选。
- `GET /adoptions/{no}` 仅本人或管理员可读，联系方式按角色脱敏，返回缴费流水、审核说明与缴费期限。
- `PUT /adoptions/{no}/payment` 仅管理员可调用；只允许 `PENDING_PAYMENT`，必须带至少一张凭证；实收与应收不一致且未填写差额说明时返回 30408。
- 入账事务同时写 `adoption_payment`、认养单转 `ACTIVE`、计算起止日期与证书号、动物转 `ADOPTED`、清空缴费期限；任一步更新 0 行即抛出 50002。
- `PUT /adoptions/{no}/terminate` 仅管理员可调用；只允许 `ACTIVE`，原因不少于 5 字，终止后动物恢复 `ON_CAMPUS`。
- `GET /adoptions/{no}/certificate` 仅本人或管理员可读；未入账或没有证书号返回 30412。
- 前端提供认养申请、管理员审核 / 缴费入账、我的认养、详情 / 终止、可打印证书；构建阶段类型检查必须通过。

### RED

- 先新增 `AdoptionQueryServiceTests`（8）、`AdoptionPaymentServiceTests`（8）、`AdoptionLifecycleServiceTests`（4）、`AdoptionLifecycleControllerTests`（7），共 27 例。
- 首次运行因 `AdoptionQueryService`、`AdoptionPaymentService`、`AdoptionLifecycleService`、`AdoptionQueryService` 等目标类型不存在而编译失败，确认不是断言失败冒充 RED。
- 真库测试首次运行暴露 V13 迁移使用 MySQL 不支持的 `ADD COLUMN IF NOT EXISTS`，在 Flyway 阶段失败。

### GREEN 与重构

- 新增 V13：认养单补 `certificate_no`、`pay_deadline`、`termination_reason` 与证书号唯一索引。
- 新增查询服务、缴费服务、终止服务与三个控制器；原有申请 / 审核控制器不改，避免破坏既有测试。
- 查询 Mapper 使用扁平联表行映射，分页与详情共用；管理员列表计数改为 `#{userId} IS NULL OR user_id = #{userId}`，修复空用户条件下的管理员列表计数。
- 缴费服务先锁认养单、再锁动物，写缴费 → 标动物 → 转 ACTIVE；日期按 `paidAt + period.months()` 计算，证书号与缴费流水号均由 `seq_no` 发号。
- 审核 SQL 同步写 `pay_deadline = auditedAt + 7 DAY`，避免详情页用估算时间。
- 前端新增 `api/adoptions.ts`、`AdoptView`、`AdoptionDetailView`、`AdoptionCertificateView`、`AdoptionPaymentDialog`；证书采用页面排版 + 浏览器打印 / 另存 PDF，服务端负责签发编号与数据。

### 测试结果

- 新增相关单元 / 控制器测试 27/27 通过。
- 完整套件 309/309 通过，0 失败、0 错误、0 跳过；其中单元 / 控制器测试 278 例、真实 MySQL IT 31 例。
- 新增 `AdoptionLifecycleIT` 2/2，覆盖审核 → 入账 → ACTIVE → 详情 / 我的 / 管理员列表 → 终止 → 证书。
- 前端 `npm ci` 后 `npm run build` 通过（`vue-tsc` 类型检查 + Vite 构建）；chunk 体积告警不影响功能。
- 仍未完成：到期自动提醒 / 释放、PDF 服务端直接输出；证书当前由前端打印生成 PDF。
- 第四轮提交 `125a43c` 已推送至 `origin/feature/d3-adoption`，并创建 PR #9：https://github.com/fusheng13145/critter-codex/pull/9 。

## 第五轮：消息中心与认养通知（2026-11-21）

### 验收标准

- `GET /messages` 只返回 Bearer 当前用户的消息，支持 `readFlag/type/page/size`；类型非法返回 30303。
- `GET /messages/unread` 固定返回认养到期、募捐进度、工单进展、审批结果四个计数，缺失类型补 0。
- `PUT /messages/read` 支持批量编号；不存在或不属于当前用户的消息逐条返回 30410，其余照常成功；空编号返回 30301。
- 消息列表、未读统计与筛选统一只在最近 180 天内生效。
- 认养审核通过/驳回、缴费入账、终止均在同一事务内为申请人写入 `APPROVAL_RESULT` 消息；消息写入失败返回 50002 并回滚。
- 前端 `/messages` 展示四类未读卡片、类型/已读筛选、分页、批量已读与业务详情跳转，构建必须通过。

### RED

- 先新增 `MessageQueryServiceTests`（4）、`MessageReadServiceTests`（5）、`MessageControllerTests`（4），并改造审核/缴费/终止测试断言通知落库。
- 首次运行因 `com.just.critter.message.repository`、`message.service` 包与 `MessageRepository` 等类型不存在而编译失败，确认 RED。
- 真库 `MessageIT` 首次运行暴露 `message.type VARCHAR(16)` 无法容纳 17 字符的 `DONATION_PROGRESS`；随后动态 SQL 中 `&gt;=` 被当作字面量，MySQL 报语法错误。

### GREEN 与重构

- 新增 `MessageEntity/MessageRow/MessageTypeCountRow`、`MessageMapper`、`MyBatisMessageRepository`。
- 新增 `MessageQueryService`、`MessageReadService`、`MessageController` 与请求 / 响应模型；列表与计数共用 180 天窗口。
- 新增 V14，将 `message.type` 调整为 `VARCHAR(24)`；Mapper 动态 SQL 改用原生 `>=`。
- 审核、缴费、终止服务注入 `MessageRepository`，在业务更新之后写消息；写入 0 行抛 50002，保证同事务。
- 前端新增 `api/messages.ts`、`MessageView.vue`，`/messages` 路由由占位页切换为实际页面；消息类型与标签进入 `types.ts` / `format.ts`。
- 补 `MessageIT`：真实 MySQL 验证 180 天窗口、未读分组、部分成功批量已读；`AdoptionLifecycleIT` 增加 2 条（审核+入账）与终止后共 3 条通知断言。

### 测试结果

- 消息新增测试 13 例；连同受影响的审核 / 缴费 / 终止测试，相关测试 32/32 通过。
- 完整套件 323/323 通过，0 失败、0 错误、0 跳过；其中单元 / 控制器 291 例、真实 MySQL IT 32 例。
- `MessageIT` 1/1、`AdoptionLifecycleIT` 2/2 通过。
- 前端 `npm run build` 通过（`vue-tsc` + Vite）；仅有 chunk 体积告警。
- 仍未完成：认养到期自动提醒 / 释放、B 域募捐与工单事件通知、服务端直接输出 PDF 证书。
- 第五轮提交 `af329a7` 已推送至 `origin/feature/d3-messages`，并创建 PR #10：https://github.com/fusheng13145/critter-codex/pull/10 。

## 第六轮：合作医院与就诊记录（2026-11-21）

### 验收标准

- `GET /api/v1/clinics` 登录可读，返回合作医院名称、联系人、电话、地址与示例报价；示例数据不得当作真实报价。
- `POST /api/v1/visits` 仅管理员可调用；`animalId`、`clinicId`、`visitedAt`、`cost` 必填，费用为整数分且不小于 0。
- 到院时间不得晚于当前时间；主诉、诊断、处置、来源备注最长 255 字符；影像编号不得包含空字符串。
- 无 `orderId` 时 `sourceNote` 必填；有 `orderId` 时必须存在且与就诊动物一致，否则 30301。
- 动物、医院或工单不存在返回 30410；普通认养人登记返回 20003。
- `visitNo` 采用 `VS + 到院日期 + 3 位年度序号`，序号复用 `seq_no`。
- `GET /api/v1/visits/{no}` 登录可读，返回诊断、处置、费用、影像编号、来源备注与关联工单；不存在返回 30410。
- 前端 `/medical` 替换占位页，提供医院列表、管理员登记、编号查询与详情抽屉；构建必须通过。

### RED

- 先新增 `VisitServiceTests`（9）、`MedicalQueryServiceTests`（3）、`MedicalControllerTests`（6）、`MedicalIT`（1），共 19 例。
- 首次 `mvn test-compile` 因 `com.just.critter.care.service`、`care.repository` 等包与 `VisitService`、`MedicalRepository` 等类型不存在而编译失败，确认不是断言失败冒充 RED。
- 唯一一次 GREEN 断言失败是测试对 `orderId` 错误数据要求完全等于 `{field=orderId}`，实现额外带 `reason`；调整为校验 `field`，不放宽业务校验。

### GREEN 与重构

- 新增 `ClinicView`、`VisitCommand`、`VisitWrite`、`VisitDetail`、`VisitRegistration` 与动物/医院/工单引用快照。
- 新增 `MedicalRepository` / `MyBatisMedicalRepository`、`MedicalMapper`、`VisitEntity` 与四个联表行模型。
- 新增 `MedicalQueryService`、`VisitService`、`ClinicController`、`VisitController`、`VisitCreateRequest`。
- 登记流程：校验 → 查动物 → 查医院 → 校验工单与动物一致 → 按到院年份发号 → 单条插入 → 返回摘要；写入 0 行抛 50002。
- 新增 V15，为 `visit` 补 `source_note`；未把来源塞进 `complaint`。
- 前端新增 `api/medical.ts`、`MedicalView.vue`，切换 `/medical` 路由；费用按元录入、提交换算为分，登记后自动查询详情。
- 新增 `docs/T3.3-医疗协作与就诊记录说明.md`，记录编号格式、来源备注列与未完成接口。

### 测试结果

- 新增相关单元 / 控制器 18/18、`MedicalIT` 1/1 通过。
- 本轮实测完整后端：单元 / 控制器 309 例、真实 MySQL IT 39 例，合计 348 例，0 失败、0 错误、0 跳过。
- 说明：默认 `mvn test` 的 surefire 不执行 `*IT`，此前记录中“完整套件”的 IT 数量来自单独汇总，容易低估；本轮以 `clean test` 加 `-Dtest='*IT'` 两次实测计数为准。
- 前端 `npm run build` 通过（`vue-tsc` + Vite），仅有 chunk 体积告警。
- 仍未完成：`POST /clinics`、`PUT /visits/{no}`、按档案编号选择动物、附件上传与影像授权访问。

### 合并与交付

- PR #10 因 `main` 合入成员 B 页面后，在 `critter-frontend/src/api/types.ts` 与路由产生冲突；在 `feature/d3-messages` 上合入 `origin/main`，保留 D2 域与 D3 消息两套类型，解决后提交 `eecb500` 并推送。
- PR #10 现已显示 “No conflicts with base branch”，可自动合并。
- 医疗分支已重放到更新后的 `feature/d3-messages`，提交 `855f513`；`types.ts` 再次冲突，最终顺序为 D2 域类型、消息类型、医疗类型，路由保留 B 的页面与医疗页。
- 推送 `origin/feature/d3-medical` 并自动创建 PR #12：https://github.com/fusheng13145/critter-codex/pull/12 。
- PR #12 base 为 `feature/d3-messages`，显示无冲突；待 #10 合入后 retarget 到 `main`。
- 重放后复跑：单元 / 控制器 309、真实 MySQL IT 39，合计 348，0 失败；前端构建通过。

## 第七轮：公告列表、发布与详情（2026-11-21）

### 验收标准

- `GET /api/v1/notices` 公开可读，即使携带无效 Bearer 也不拦截；`category` 可选，`page/size` 分页。
- 分类英文码固定为 `PROGRESS / PUBLICITY / SCIENCE`；非法分类返回 30303。
- 列表只返回 `published_at` 非空记录，排序为 `pinned DESC, published_at DESC, id DESC`；空结果不查询明细列表。
- `POST /api/v1/notices` 仅 `ANIMAL_ADMIN / SYS_ADMIN / SYSTEM_ADMIN` 可调用；普通学生返回 20003。
- 标题 trim 后 1–128 字，正文 trim 后 1–20000 字；字段非法 30301，分类非法 30303，写入 0 行 50002。
- `authorId` 取 Bearer 当前用户，发布即写 `published_at`；公告编号为 `NT + yyyyMMdd + 3 位年度序号`，复用 `seq_no` 的 `NOTICE` 键。
- `GET /api/v1/notices/{id}` 公开可读，返回正文与发布人昵称；id 非法 30301、不存在 30410。
- 前端新增公告列表与详情页，管理员可在列表页发布；构建必须通过。

### RED

- 先新增 `NoticeServiceTests`（8）、`NoticeControllerTests`（6）、`NoticeIT`（1），共 15 例。
- 首次 `mvn test-compile` 因 `com.just.critter.notice.repository`、`notice.service` 包及 `NoticeService`、`NoticeRepository` 等类型不存在而编译失败，确认 RED；失败原因仅为缺失类型，不是断言失败。
- 测试装配阶段发现 `/api/v1/notices/**` 原在 `OPTIONAL_AUTH`，该拦截器不做角色判定，导致学生发布返回 200；将公告从可选鉴权移出，改由 `@PublicEndpoint` 放行两个 GET、`@RequireRoles` 拦截 POST。
- 控制器测试另修正两处测试自身问题：Mockito 对无 `equals` 的 `PageQuery` 用实例匹配会返回 null，改为 `any(PageQuery.class)`；业务失败按项目既有约定默认 HTTP 200，断言改为 `status().isOk()` 并保留 `code=30303`。

### GREEN 与重构

- 新增 `NoticeCategory`、`NoticeCommand`、`NoticeView`、`NoticeDetail`、`NoticeSummaryRecord`、`NoticeWrite`。
- 新增 `NoticeRepository` / `MyBatisNoticeRepository`、`NoticeMapper`、`NoticeEntity` / `NoticeRow`。
- 新增 `NoticeService`、`NoticeController`、`NoticeCreateRequest`；列表先 count，0 条直接返回空页；发布先校验再取号，避免非法请求消耗序号。
- `NoticeMapper.findById` 只查 `published_at` 非空记录并左连 `sys_user.nickname`；不存在按 30410 处理。
- `notice` 表字段已满足本轮需求，未新增迁移；`notice_no` 唯一键与 `(published_at, pinned)` 索引沿用 V1。
- 前端新增 `api/notices.ts`、`NoticeListView.vue`、`NoticeDetailView.vue`，注册 `/notices` 与 `/notices/:id`；消息中心增加“校园公告”入口。列表支持分类切换、置顶标记与分页；管理员发布对话框校验标题/正文非空。
- 同步文档：`03 接口设计说明书.md` 补 7.14 公告三接口、分类枚举与编号规则，并把“公告逻辑删除”修正为“当前不提供删除/撤回接口”。

### 测试结果

- 新增相关测试 15/15 通过；其中单元 8、控制器 6、`NoticeIT` 1。
- 本轮实测完整后端：单元 / 控制器 323 例、真实 MySQL IT 40 例，合计 363 例，0 失败、0 错误、0 跳过。
- 前端 `npm run build` 通过（`vue-tsc` + Vite），仅有 chunk 体积告警。
- 仍未完成：公告删除/撤回、首页置顶摘要、附件与富文本；档案详情反查就诊、三类消息生产与认养到期任务仍待后续轮次。

## 第八轮：档案详情反查就诊（2026-11-21）

### 验收标准

- `GET /api/v1/visits?animalCode=...` 登录可读，返回该动物全部就诊，按到院时间倒序。
- `animalCode` 必填；空白 30301，档案不存在 30410，档案存在但无就诊返回空数组。
- 返回字段与 `GET /visits/{no}` 一致；不改动 35 接口清单计数。
- 档案详情页必须能展示该面板；无记录时给出明确空态。

### RED

- `MedicalQueryServiceTests` 新增 3 例、`MedicalControllerTests` 新增 3 例，并在 `MedicalIT` 扩展现有真库用例，断言按动物编号反查。
- 首次定向运行因 `MedicalRepository.findAnimalByCode / findVisitsByAnimal` 与 `MedicalQueryService.visitsByAnimal` 不存在而编译失败，确认 RED。

### GREEN

- `MedicalMapper` 新增 `findAnimalByCode` 与 `findVisitsByAnimal`；后者联表动物、医院与工单，`ORDER BY visited_at DESC, visit_no DESC`。
- `MedicalRepository / MyBatisMedicalRepository` 新增对应方法，并把 `VisitDetail` 映射抽成 `toDetail`，避免按编号与按动物两条查询重复拼装。
- `MedicalQueryService.visitsByAnimal` 先校验档案存在再查列表；`VisitController` 新增根路径 GET，缺少 `animalCode` 交给服务返回 30301。
- 前端新增 `AnimalVisitPanel.vue` 与 `fetchAnimalVisits`，嵌入 `AnimalDetailView`；无记录显示空态，失败显示错误文案。

### 测试结果

- 医疗相关 25/25 通过：`VisitServiceTests` 9、`MedicalQueryServiceTests` 6、`MedicalControllerTests` 9、`MedicalIT` 1。
- 本轮完整后端：单元 / 控制器 329、真实 MySQL IT 40，合计 369，0 失败、0 错误、0 跳过。
- 前端 `npm run build` 通过，仅有 chunk 体积告警。
- UI 仅完成构建校验，未做浏览器端手工点验；后续交付前应在真实数据下检查档案详情面板。

## 第九轮：募捐/工单消息生产与认养到期任务（2026-11-21）

### 验收标准

- `DONATION_PROGRESS`：捐赠核对为 `VERIFIED` 或 `ABNORMAL` 时写消息；只通知实名捐赠人，匿名不写。
- `ORDER_PROGRESS`：工单每次有效状态流转后通知上报人，标题含工单号与状态中文。
- `ADOPTION_EXPIRY`：每天 02:00 扫 `end_date = today+7` 且 `ACTIVE` 的认养，发一次提醒；扫 `end_date < today` 且 `ACTIVE` 的认养，转 `EXPIRED` 并把动物恢复 `ON_CAMPUS`、`adopted=0`。
- 三类消息均与业务同事务；消息写入 0 行返回 50002，整笔回滚。到期任务按 `(type,biz_id,receiver_id)` 去重，同一天重跑不重复。
- 任务执行结果写 `sys_log`（`JOB_ADOPTION_EXPIRY`），便于验收演示。

### RED

- `DonationServiceTest` 新增 3 例、`OrderServiceTest` 新增 1 例、新建 `AdoptionExpiryServiceTests` 5 例；`DonationIT`、`OrderIT` 增加消息行断言并新建 `AdoptionExpiryIT`。
- 首次定向运行因 `AdoptionExpiryRepository`、`AdoptionExpiryService`、`ExpiringAdoption`、`MessageRepository.exists` 不存在而编译失败，确认 RED。

### GREEN

- `MessageMapper` 新增 `countByBiz(type,bizId,receiverId)`，仓储暴露 `exists` 供到期任务去重。
- `DonationService` 在核对成功/异常后写 `DONATION_PROGRESS`；`OrderService` 在每次流转后写 `ORDER_PROGRESS`，两者都在写入 0 行时抛 50002。
- 新增 `AdoptionExpiryMapper / MyBatisAdoptionExpiryRepository`、`AdoptionExpiryService`、`AdoptionExpiryJob`；`CritterBackendApplication` 开启 `@EnableScheduling`，任务按 `Asia/Shanghai` 每日 02:00 触发。
- 到期释放只更新 `ACTIVE` 认养与 `ADOPTED` 动物，SQL 条件保证重跑幂等；提醒先查 `message` 去重再插入。
- 真库用例踩到两处夹具问题并修正：接收人必须用种子用户 `id=1`，否则 `fk_message_receiver` 拒绝；动物编号必须控制在 `VARCHAR(20)` 内。

### 测试结果

- 新增 9 例通过：捐赠 3、工单 1、到期任务 5。
- 真实 MySQL：`DonationIT` 验证入账消息、`OrderIT` 验证两次流转两条消息、`AdoptionExpiryIT` 验证提醒去重与过期释放。
- 完整后端：单元 / 控制器 338、真实 MySQL IT 41，合计 379，0 失败、0 错误、0 跳过。
- 前端无新增页面，沿用消息中心已有跳转；未重新构建前端（本轮无前端改动）。
- 未做手工触发定时任务验收；测试通过 `process(LocalDate)` 直接覆盖规则。

## 第十轮：新增合作医院与就诊纠错（2026-11-21）

### 验收标准

- `POST /api/v1/clinics` 仅管理员可用，新增医院返回自增 `clinicId`，并写 `CLINIC_CREATE` 审计。
- 医院名称必填且 trim 后 ≤64 字；联系人、电话各 ≤32 字；地址、报价各 ≤255 字；空白可选字段落库为 `NULL`。
- `PUT /api/v1/visits/{no}` 仅管理员可用，只改主诉、诊断、处置、费用与影像；动物、医院、到院时间与来源备注不变。
- 纠错原因必填且 ≤255 字；未填返回 30401，记录不存在 30410，字段非法 30301，更新 0 行 50002。
- 纠错成功写 `VISIT_CORRECT` 审计，`before_json` 与 `after_json` 能还原修改前后内容。

### RED

- 新增 `ClinicServiceTests` 5 例，`VisitServiceTests` 增加 5 例，`MedicalControllerTests` 增加 4 例。
- 首次 `mvn test-compile` 因 `ClinicService`、`ClinicCommand`、`ClinicWrite`、`VisitUpdateCommand`、`VisitUpdateWrite` 不存在而编译失败，确认 RED。
- 第二次编译暴露测试自身问题：`insertClinic` 返回 `long`，Mockito 需要 `thenReturn(0L)` 而不是 `thenReturn(0)`。

### GREEN

- 新增 `ClinicService`、`ClinicCommand`、`ClinicWrite`、`ClinicCreateRequest`、`ClinicEntity`；`MedicalMapper.insertClinic` 用 `@Options(useGeneratedKeys)` 回填 `clinicId`。
- `VisitService` 注入 `AuditLogService`，新增 `update`；`VisitUpdateCommand` 与 `VisitUpdateWrite` 分离，原因不落 `visit` 表。
- `MedicalMapper.updateVisit` 以 `visit_no` 更新五个可编辑字段与 `updated_at`；`MedicalRepository` 增加 `insertClinic` 与 `updateVisit`。
- `VisitController` 增加 `PUT /{no}`，`ClinicController` 增加 `POST`，均用 `@RequireRoles` 限制管理员。
- 前端 `medical.ts` 增加 `createClinic` 与 `updateVisit`；`MedicalView.vue` 增加新增医院弹窗和就诊详情纠错弹窗，普通用户不可见入口。

### 测试结果

- 新增 14 例单元/控制器：`ClinicServiceTests` 5、`VisitServiceTests` 5、`MedicalControllerTests` 4。
- `MedicalIT` 追加 1 例：真实 MySQL 新增医院、自增编号、纠错字段、影像 JSON 与 `sys_log` 前后快照。
- 合并 `origin/main`（PR #15、PR #17）后完整后端：单元 / 控制器 353 例、62 suites，真实 MySQL IT 42 例、15 suites，合计 395 例，0 失败、0 错误、0 跳过。
- 合并时 C 验收记录按“第九轮 → 第十轮”顺序解决 EOF 冲突；同时发现 `main` 出现两个 `V14` 迁移（`message_type_width` 与 B 的 `expense_double_sign_stage`），Flyway 启动即失败。已将 B 的支出迁移顺延为 `V17__expense_double_sign_stage.sql`，不改既有业务 SQL，清理构建产物后全量 IT 恢复通过。
- 前端 `npm run build` 通过（`vue-tsc` + Vite），仅有 chunk 体积告警。
- 未做浏览器端手工点验；后续演示前应实际新增一家医院并纠错一条就诊，确认审计日志可在库中查到。
