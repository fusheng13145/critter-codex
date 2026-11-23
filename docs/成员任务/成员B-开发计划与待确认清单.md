# 成员 B · 开发计划与待确认清单

> 本文基于 2026-11-21 仓库快照（HEAD `e8209da`）编写。输入为《需求分析说明书》《功能模块拆解与开发分工说明书》《功能分析说明书》《接口设计说明书》《数据库设计说明书》《概要设计说明书》《项目计划与三人进度表》、三份成员任务书，以及 `frontend-prototype/青绿自然.html` 静态原型。
> 文中**结论**均可在上述文档中定位到出处；**建议**标注为建议，未经确认不作为既定事实。

---

## 1 项目定位

一句话定位：**青檐万灵册不是宠物领养商城，而是校内动物档案 + 救助资金台账工具，附带一个轻量的信息发布与参与入口。**

《需求分析说明书》§1.3 给出五条建设思路，构成整个系统的取舍基准：

| # | 建设思路 | 对实现的直接约束 |
|---|---|---|
| 1 | 云认养与领养严格分开，一期只做云认养 | 认养是"有起止时间的资助关系"，不是把动物带走；领养模块三期以后 |
| 2 | 资金只做台账，不做支付 | 线下转账 + 上传凭证 + 管理员核对入账；不接支付、不做退款 |
| 3 | 每笔支出同时挂靠"项目、动物、病历" | `expense` 的 `project_id`、`animal_id` 均 NOT NULL，可一条 SQL 追到底 |
| 4 | 动物档案用状态机组织，不是自由编辑的表格 | 每次迁移留操作人、时间、原因；非法迁移拒绝并回传允许集合 |
| 5 | 档案分三层（个体／群体／出现记录） | 三张主表物理分离，共用点位与附件，统计口径各自独立、禁止 union 计数 |

系统边界（§2.4）：**不做**在线支付与退款、物流配送、动物买卖与配种、面向社会的公开募捐、第三方地图与短信通道、兽医在线诊断、野生动物的捕捉驱赶决策。

## 2 核心功能

十个业务模块、65 个功能点、118 个接口、37 张表。全部功能围绕一条九环节主链路展开（《功能分析说明书》§3）：

| 环节 | 触发 | 完成标志 | 模块 | 负责人 |
|---|---|---|---|---|
| 1 发现与上报 | 学生拍照提交 | 生成上报编号，进入待办 | M06 | B |
| 2 受理与建档 | 管理员受理 | 生成工单，档案建立或关联 | M06、M02 | B |
| 3 认养申请 | 学生在档案页点申请 | 待审核单，动物锁定 | M03 | C |
| 4 认养审核 | 管理员核对学籍与数量 | 转待缴费 | M03 | C |
| 5 缴费入账 | 线下转账后上传凭证 | 关系生效，动物标记已认养 | M03 | C |
| 6 募捐发起 | 管理员立项 | 项目上线，公开页可见 | M04 | B |
| 7 捐赠入账 | 学生登记、管理员核对 | 项目已筹金额上升 | M04 | B |
| 8 医疗与支出 | 送医后登记就诊 | 支出挂项目与动物，余额下降 | M05、M04 | C、B |
| 9 结项公示 | 项目到期或达标 | 公示页发布且不可篡改 | M04、M08 | B、C |

链路上三处卡口，也是 B 的全部技术风险所在：

| 卡口 | 规则 | 落地方式 | 拒绝码 |
|---|---|---|---|
| 动物唯一认养 | BR-02 | 服务层锁 + 生成列唯一索引 `uk_adoption_active` + 提交/审核两处区间校验 | 30404 |
| 支出不超余额 | BR-08 | 事务内 `SELECT ... FOR UPDATE` 锁项目行，同事务算余额再插入 | 40103（返回差额） |
| 超 500 元双人复核 | BR-09 | 服务层判 `复核人 ≠ 登记人` + 库层 CHECK | 40104 / 40105 |

## 3 目标用户

六类角色（《需求分析说明书》§2.3），主角色唯一，认养人／志愿者为叠加身份：

| 角色 | 取得方式 | 核心诉求 | 系统给什么 |
|---|---|---|---|
| 游客 | 无需登录 | 想看结果 | 公开档案、项目进度、公示公告 |
| 注册学生 | 学号 + 校园邮箱，学籍校验 | 参与但怕麻烦 | 三步上报、捐赠登记、认养申请、个人记录 |
| 认养人 | 存在生效认养关系 | 看到"我照顾的那只" | 专属动态、续认养、探视预约、电子证书 |
| 志愿者 | 申请 + 审核 | 知道能干什么 | 任务接单、打卡、巡查记录（三期） |
| 动物管理员 | 社团／后勤指定 | 日常管理 + 资金不出错 | 建档、受理、审核、募捐、支出（支出需他人复核） |
| 系统管理员 | 项目组指定 | 系统可维护、可追责 | 用户、权限、字典、日志、备份；**无业务审核权** |

规模口径（《概要设计说明书》§1）：注册 5000、日活 300、峰值 50 并发。真实使用场景是"社团 3—5 名管理员 + 数百名学生参与者 + 毕业交接"，因此"别人能接手"比"功能多"更重要。

## 4 成员 B 的角色定位

| 项 | 内容 | 出处 |
|---|---|---|
| 角色名 | 后端负责人 / 档案与资金域（D2） | 分工说明书 §6 |
| 主责 | D2 全部 **52 个接口**：档案三层数据、状态机、上报与工单、项目与资金事务、台账对账 | 分工说明书 §2、成员 B 任务书 |
| 兼责 | 工程骨架、数据库建模（37 张表主笔）、部署与备份、**指标口径定义** | 分工说明书 §6 |
| 答辩切片 | SL-2 档案与资金（P02 档案中心、P04 资金、P06 上报 → 工单） | 分工说明书 §6.1 |
| 讲解难度 | 高：状态机表驱动、余额行锁、双人复核全在这条线上 | 同上 |
| 文档责任 | DOC-03 功能分析说明书（主笔）、DOC-05 数据库设计说明书（主笔）、DOC-04 接口文档复核 | 分工说明书 §10 |

**职责的实质**：B 是"数据可信"这一层唯一负责人。评审查代码时问的三件事——编号为什么不重、余额为什么不会负、谁复核的——都落在 B 的模块里。

## 5 负责模块与交付范围

### 5.1 模块归属

| 模块 | 名称 | 功能点数 | B 承担的功能点 |
|---|---|---|---|
| M02 | 生物档案管理 | 13 | FR-A-01～06、09～13（10 个主责；FR-A-07 二维码页面归 A） |
| M06 | 上报与巡查管理 | 5 | FR-R-01、02、04、05（FR-R-03 进展反馈走 C 的消息通道） |
| M04 | 募捐与资金管理 | 10 | FR-F-01、02、04、05、06、07、08、09（FR-F-03 由 C 代写、B 验收；FR-F-10 凭证模板归 C） |
| M05 | 医疗救助（部分） | 2 | FR-M-05 费用与项目关联、FR-M-06 救助工单流转（其余归 C） |
| M09 | 统计（口径） | — | FR-S-01／02 的指标口径与视图，页面与接口归 A |

### 5.2 接口清单结构（52 个）

| 分组 | 数量 | 内容 | 迭代 |
|---|---|---|---|
| 生物档案与基础数据 | 15 | `/animals`（列表、建档、详情、编辑、状态、历史、健康读、健康写）、`/groups`（列表、建档、详情）、`/species` 列表、`/occurrences`（列表、登记）、`/points` 列表 | 一期 |
| 上报与工单 | 7 | `POST/GET /reports`、`/reports/{id}/accept`、`GET /orders`、`/orders/{no}`、`PUT /orders/{no}/status` | 一期 |
| 募捐／捐赠／支出／结项 | 10 | `/projects`（列表、发起、详情、审核、结项）、`/donations`（登记、核对入账）、`/expenses`（登记、列表、复核）、`/ledger` | 一～二期 |
| 后续增强 | 20 | 档案合并、二维码、群体编辑／目击归属／合并拆分、点位与物种维护、上报详情与无效关闭、项目草稿编辑、结项公示详情、台账导出、捐赠列表／异常标记／凭证 | 三期 |

### 5.3 数据表（16 张，加 3 个统计视图）

`animal`、`animal_status_log`、`animal_change_log`、`health_record`、`animal_group`、`sighting`、`occurrence`、`base_point`、`base_species`、`report`、`rescue_order`、`project`、`project_budget`、`donation`、`expense`、`project_settlement`；视图 `v_animal_overview`、`v_group_overview`、`v_project_fund`。
B 另外主笔其余 21 张表的建模（A 的 8 张权限表、C 的 13 张认养／医疗／内容表），但只负责结构评审，不负责业务实现。

### 5.4 前端页面责任（P02 / P04 / P06）

骨架由 A 统一建，**字段绑定、校验规则、联调由 B 完成**：

| 页面组 | 路由 | B 负责的部分 |
|---|---|---|
| P02 档案中心 | `/animals`、`/animals/:id`、`/groups`、`/species` | 列表筛选五条件、状态标签与迁移弹窗（回显 allowed[]）、编辑留痕展示、健康记录时间线 |
| P04 资金 | `/projects`、`/projects/:id`、`/ledger`、`/expenses` | 进度条（登记≠入账两列分开）、支出登记余额提示（含差额）、复核双签状态、台账与结项公示 |
| P06 上报 | `/report`、`/report/mine`、`/orders` | 三步表单校验（描述 ≥10 字、照片 ≥1、5MB）、超时工单高亮、工单时间线 |

## 6 与其他成员的协作边界

| 对象 | B 提供 | B 消费 | 红线 |
|---|---|---|---|
| A（D1） | 三个统计视图与口径、档案与资金查询接口、项目进度数据 | 字典读取、附件上传与审核、令牌与角色判断、日志写入入口、脱敏序列化 | B 不改 `sys_*` 表；统计页面 A 只消费 B 的口径，不自行定分子分母 |
| C（D3） | `GET /animals/{code}` 状态查询与状态流转、项目进度与余额查询 | 认养状态、就诊与 TNR 记录摘要、消息通道 | B 不改 `adoption`／`visit`／`message` 表；C 不 join `animal`／`project` 表 |
| 共用 | `donation` 表：B 主责，C 只写 `POST /donations` 一个接口，B 验收 | — | 同一张表的写入口只能有一个；C 的写接口必须走 B 定义的字段与校验 |

三条共同约束（分工说明书 §6.1、§7）：**跨切片只调接口，不改对方的表与服务**；**接口契约冻结后只许新增字段**；**字段变更必须在修订记录登记，未登记的视为缺陷**。

## 7 现状盘点（文档 vs 代码）

| 维度 | 文档要求 | 仓库现状 | 差距 |
|---|---|---|---|
| 文档 | 需求 + 6 份设计文档 + 3 份成员任务 | 全部已就位；接口契约标 `v1.0-frozen`（2026-11-21） | 基本无 |
| 后端 | Spring Boot 3.x + MyBatis-Plus + MySQL 8 + Redis，四层分层 + 横切 | 仅启动类 1 个；`pom.xml` 为 **Spring Boot 4.0.10 + mybatis-spring-boot-starter 4.0.1 + Java 21**；无 controller/service/mapper/domain | 严重：技术栈与文档不一致，业务代码 0 |
| 前端 | Vue 3 + Vite + Pinia + Element Plus，12 个页面组 | Vite + Vue 3 + TS 脚手架（`HelloWorld.vue`），未装 router/pinia/element-plus | 严重：页面 0 |
| 数据库 | 37 表 + `db/migration` 递增脚本 + seed 与演示数据分离 | 无 `db/` 目录、无 DDL、无 `application.yaml` 数据源配置 | 严重：B 的第一阻塞项 |
| 原型 | 12 个页面组线框 | 单文件静态 HTML，7 个视图：工作台／档案列表／档案详情／认养／上报／资金／消息 | 中：P02、P04、P06 有原型（B 受益），P00／P05／P07／P09／P10／P11 缺 |
| Git | main 保护 + `feature/{域}-{功能点号}` | 仅 `main`，3 次提交（`c93ef0f` → `00f72cc` → `e8209da`），无功能分支 | 中：协作规范未落地 |

原型可读出的设计语言（对 B 联调有用）：`--accent:#2f6b57` 青绿主色、`--bg:#f6f8f4`、卡片圆角 14px、状态用 `tag` 三色（正常／警告／灰）；资金页把"已筹／目标／已复核支出／可用余额"四个数并列，与本项目"登记 ≠ 入账"的口径一致；档案详情页含"状态时间线"，正好是 `animal_status_log` 的展示位。

## 8 后续开发计划

### 8.1 排期前提

文档的《项目计划与三人进度表》按 **10 周**编排，课程要求写的是 **16 周周五答辩、17 周提交项目报告**。二者不一致，且当前教学周未知（见 Q-B-01）。因此下列计划按**周相对制**编排：W0 为本周剩余时间，W1—W4 依次推进；**若实际只剩约 4 周，按本表执行并放弃第二部分的 20 个后续接口**；若时间充裕，把 W4 的交付工作后移，用多出的周补档案二期（合并、群体拆分、二维码）。

建议的压缩原则（对 B 有利）：**先保 P0 的 32 个基础接口与一条可演示闭环，20 个后续接口全部后置**。分工说明书 §9 已写明"资金模块被低估"是首要风险，压缩时先砍体验项，不砍余额与复核两条底线。

### 8.2 阶段目标

| 阶段 | 目标 | 完成判据 |
|---|---|---|
| W0 基座 | 技术栈定版、工程骨架、37 表 DDL、枚举与种子字典 | `GET /api/v1/ping` 返回统一响应体；空库一键建表；枚举与接口文档第 10 节一致 |
| W1 档案纵切 | 建档发号、编辑留痕、状态机引擎、健康记录、群体／目击／出现记录 | 连续建三只猫编号不跳不重；已离世改治疗中被拒并回 `allowed[]`；改体重可查到前后值 |
| W2 上报与项目 | 上报 → 受理转工单 → 24h 超时；项目发起、预算校验、审核上架、进度展示 | 上报 24h 未受理转 `TIMEOUT` 并高亮；预算 3200／目标 3000 被拒（40101）；入账后进度实时变 |
| W3 资金闭环 | 捐赠核对入账、支出登记（行锁）、复核双签、结项、台账、3 个视图 | 余额 300 支出 500 被拒并提示差额 200；自复核 40104；有未复核支出结项 40105；台账合计 = 入账 − 已复核支出 |
| W4 联调与交付 | 并发用例、部署、备份、演示数据、答辩切片 | 并发建档不重号；两人同时认养同一只仅一单成功；一键起服务 + 一键重置数据；4 分钟切片能独立讲完 |

### 8.3 任务、优先级与依赖

| 编号 | 任务 | 优先级 | 对应功能点／接口 | 关键依赖 | 交付物 |
|---|---|---|---|---|---|
| T0.1 | 技术栈定版并写决策记录 | P0 | — | Q-B-02 | ADR-001（Boot 版本、MyBatis-Plus 取舍、Redis 是否启用） |
| T0.2 | 统一响应体、全局异常、分页封装、时间与金额序列化 | P0 | 接口文档 §2、§3 | T0.1 | `support` 包 + `BusinessException` + `Result<T>` |
| T0.3 | 37 张表 DDL、`seq_no` 发号表、索引与 CHECK | P0 | 数据库文档 §2—§6 | Q-B-04 | `db/migration/V1__init.sql`，可在空库一次执行 |
| T0.4 | 枚举常量类与字典种子 | P0 | 接口文档 §10 | A 的 `/dicts` 契约（冻结即可） | `domain/enums`、`seed_dict.sql` |
| T0.5 | 分支与提交规范落地 | P1 | 分工说明书 §8 | Q-B-05 | `feature/d2-FR-A-01` 等分支 + PR 模板 |
| T1.1 | 编号发号器（`类别-年份-流水`，行锁） | P0 | FR-A-01 / `POST /animals` | T0.3 | 并发下不重号、不跳号 |
| T1.2 | 档案增删改查与五条件筛选、编辑留痕 | P0 | FR-A-02、03 | T1.1 | `animal_change_log` 前后值可查 |
| T1.3 | 状态机引擎 + 迁移表（动物／工单／项目） | P0 | FR-A-04、BR-12 | T0.4 | `TransitionEngine` + 非法迁移回 `allowed[]` |
| T1.4 | 健康与防疫记录、提醒待办 | P0 | FR-A-05 | T1.2 | 疫苗到期前三天提醒 |
| T1.5 | 群体档案、目击登记、物种出现记录、点位／物种只读 | P0 | FR-A-09、10、12、13 | T1.2 | 群体不可发起认养（30501）；区间校验（30504） |
| T2.1 | 学生上报（三步、同点位 24h 去重提示、野猪拦截） | P0 | FR-R-01、BR-18 | T1.2 | 提交返回编号；30503 提示流程 |
| T2.2 | 受理转工单、工单状态机、时间线 | P0 | FR-R-02、FR-M-06 | T1.3、T2.1 | 受理后 24h 时限生成，学生收到消息 |
| T2.3 | 工单超时扫描（每小时，幂等） | P0 | BR-15 | T2.2 | `TIMEOUT` + 待办高亮 + `sys_log` |
| T2.4 | 项目发起、预算校验、审核上架、进度展示 | P0 | FR-F-01、02、05 | T1.3 | 40101；上线后公开页可访问 |
| T3.1 | 捐赠核对入账（支持批量）、两态分离 | P0 | FR-F-04、BR-07 | T2.4、A 的文件接口 | 未入账不计进度；40106 |
| T3.2 | 支出登记（事务内行锁算余额） | P0 | FR-F-06、BR-08 | T2.4、T3.1 | 40103 返回差额；`expense` 必须挂项目与动物 |
| T3.3 | 支出复核（≤500 单人／>500 双签） | P0 | FR-F-07、BR-09 | T3.2 | 40104 自我复核；第二次复核才累加 `spent_amount` |
| T3.4 | 结项前置校验与公示只读 | P1 | FR-F-08、BR-10 | T3.3 | 40105；公示只能追加说明 |
| T3.5 | 台账汇总、对账与三个视图 | P0 | FR-F-09、FR-S-01／02 | T3.1—T3.3 | `v_project_fund` 结余恒等式成立 |
| T4.1 | 并发与事务用例（至少 6 条） | P0 | BR-02、BR-08、BR-09 | W3 完成 | 用例执行记录（含两人同时认养） |
| T4.2 | 部署：docker-compose、备份与恢复演练 | P1 | FR-SYS-04（兼责） | Q-B-10 | compose + 备份脚本 + 恢复记录 |
| T4.3 | 演示数据集与 `qywl.sql` 导出 | P1 | 计划 §4 | T4.2 | 200 档案／12 项目／1200 捐赠／5 群体，可一键重置 |
| T4.4 | 答辩切片脚本与讲稿（4 分钟） | P0 | 计划 §9 | W3 完成 | 讲清状态机表驱动、余额行锁、双人复核 |

### 8.4 关键依赖链

```
技术栈定版(T0.1) ──> 骨架(T0.2) ──> 全部业务代码
                        │
DDL(T0.3) ──> 发号器(T1.1) ──> 档案(T1.2) ──> 状态机(T1.3) ──┬─> 上报/工单(T2.1-2.3)
                        │                                   ├─> 项目(T2.4) ──> 捐赠入账(T3.1) ──> 支出(T3.2) ──> 复核(T3.3) ──> 台账(T3.5)
A 的鉴权/字典/文件(外部) ──┘                                   └─> C 的认养校验读 `GET /animals/{code}`
枚举与迁移表三方定稿 ──> C 的认养、A 的页面状态标签
平面图底图(外部) ──> P06 选点（无底图则降级为下拉）
C 的消息通道 ──> 工单进展、入账通知
```

逆序联调会返工。分工说明书 §5 给出的 8 个联调单元中，B 是第 2、3、6、7、8 的主导方，必须等第 1 单元（登录鉴权 + 权限拦截）可用后才开工联调。

### 8.5 交付物清单

| 编号 | 交付物 | 时点 | 验收方式 |
|---|---|---|---|
| D-B1 | `db/migration/V1__init.sql` + `seed_dict.sql` + `seq_no` | W0 | 空库一次执行成功，重复执行不报错 |
| D-B2 | 后端基座（统一响应、异常、分页、序列化） | W0 | `ping` 返回统一壳；异常不露堆栈 |
| D-B3 | 状态机引擎与迁移表 | W1 | 非法迁移回 `allowed[]`；迁移表可自动生成用例 |
| D-B4 | 基础 32 接口（档案 15 + 上报工单 7 + 资金 10） | W3 | 每条按《成员 B 任务书》§1.5 六条验收清单 |
| D-B5 | 三个统计视图 + 口径一页说明 | W3 | 与台账、档案列表数字对得上 |
| D-B6 | 并发／事务用例执行记录 | W4 | 并发认养仅一单成功；超余额与自复核被拒 |
| D-B7 | `docker-compose.yml` + 备份脚本 + `qywl.sql` | W4 | 一键起服务、一键重置演示数据 |
| D-B8 | 答辩切片讲稿与演示脚本（4 分钟） | W4 | 能答上"为什么必须行锁""双签怎么拦" |
| D-B9 | 接口归属对照表（D1／D2／D3 ↔ 接口文档分组） | W0 | 三人确认无重叠、无遗漏 |

## 9 风险

| 风险 | 触发信号 | 应对 |
|---|---|---|
| 时间口径不一致 | 文档按 10 周、课程按 16 周，当前周次未知 | 先按 4 周压缩排期（保 P0 32 接口），确认后再放开后续接口 |
| 技术栈与骨架冲突 | Boot 4.0.10 太新，MyBatis-Plus 主要适配 Boot 3.x | W0 内做半天兼容性验证；不通则回退 Boot 3.5.x（建议，见 Q-B-02） |
| 前端仍是脚手架 | W1 结束时 A 仍无 router／请求层 | 立即把 P02／P04 页面降级为"表格 + 表单"，B 提供 Mock JSON |
| B 单点 | 状态机、事务、部署全在 B | W3 做一次 40 分钟内部分享；关键代码写决策注释 |
| 联调通道受阻 | SSH 密钥被 GitHub 拒绝，当前只能 HTTPS + 凭证管理器 | 修复 SSH 公钥；或统一 HTTPS remote，避免队友推送失败 |
| 文档分组歧义 | 接口文档 6.2／6.3 按业务域分组，分工按 D1／D2／D3 | D-B9 对照表在 W0 三方签字，之后只按对照表实现 |
| 演示环境故障 | 答辩前一天服务起不来 | 本地离线演示包（jar + 前端 dist + sql），10 分钟可起 |

## 10 待确认问题

以下均**未在文档中找到定论**，按"阻塞级别"排序。标注的建议只是建议，需你确认后才生效，本文不据此推进。

### 10.1 v1.1 已定稿（2026-11-21）

| 项 | 定稿口径 | 依据 |
|---|---|---|
| 技术栈 | Spring Boot 3.x + MyBatis-Plus + MySQL 8 + Redis；现有 Boot 4.0.10 + mybatis-starter 骨架须降级（列 T0.1） | 项目负责人确认 |
| 包根 | `com.just.critter`（`com.qywl` 作废） | 同上 |
| 非法迁移响应 | `message` 中文事件说明 + `data{current,target,allowed}`，`allowed` 为允许的目标状态 | 同上 |
| 异常处理 | 严格按《接口设计说明书》§3 HTTP 列（401/403/404/409/500），其余 200 | 同上 |
| 状态码族 | 档案与认养 30402、群体 30505、工单 30602、项目 40108（一族一码） | 本轮一致性评估 |

Q-B-02 以本节为准；Q-B-06 的接口归属仍需出对照表。

| 编号 | 问题 | 影响 | 建议（待确认） | 阻塞级别 |
|---|---|---|---|---|
| Q-B-01 | 课程 16 周周五答辩、17 周交报告的确切日期；当前是第几教学周 | 直接决定 4 周压缩还是 10 周完整排期，决定砍不砍 20 个后续接口 | 若只剩 4 周，按 §8.2 执行、后续接口全部后置 | 高（阻塞排期） |
| Q-B-02 | 后端技术栈以哪份为准：文档写 Spring Boot 3.x + MyBatis-Plus + Redis，仓库实为 Boot 4.0.10 + mybatis-spring-boot-starter 4.0.1 + Java 21，且无 Redis 依赖 | 影响全部后端代码、依赖选型、答辩时"为什么用这个版本"的解释 | 已定稿：Spring Boot 3.x + MyBatis-Plus + Redis（见 §10.1），骨架降级列 T0.1 | 已决（待改代码） |
| Q-B-03 | 到底几人：分工文档与计划写三人，原型页脚写"四人小组" | 若有第四人，D2 需再切（档案 vs 资金 或 上报 vs 资金），52 接口要重新分配 | 以分工文档为准的三人；若确有第四人，建议把 FR-F-08／09／10 与后续 20 个接口整块切出 | 高 |
| Q-B-04 | MySQL 实例由谁提供：版本、端口、字符集、是否 Docker、联调库与本地库是否分开 | 没有库就无法建表、无法联调；字符集错会导致中文与 JSON 字段异常 | 已闭环：本机 MySQL 8.4、`127.0.0.1:3306`、`utf8mb4`；`critter_codex` 为主库，`critter_codex_dev` 为联调库，`critter_ro@localhost` 只读 | 已决 |
| Q-B-05 | 分支与评审规则是否执行：main 是否保护、feature 命名、是否允许直接推 main；且历史刚刚重写，其他人本地仓库需要重新同步 | 队友按旧历史提交会再次制造分叉；不保护 main 则三人互相覆盖 | 已执行：`feature/*` 开发，main 已开启保护（要求 PR、禁止强推与删除、管理员同样受限）；队友需 `git fetch && git reset --hard origin/main` | 已决 |
| Q-B-06 | 接口归属对照：接口文档按 6.1／6.2／6.3 分组，分工按 D1／D2／D3 切分；`POST /donations` 由 C 代写的交接方式与验收时点 | 同一接口两处归属易重复实现或漏做；C 代写接口若不走 B 的校验会破坏入账口径 | 已出《接口归属对照表》：118 条逐条落到 A/B/C，31／52／35 与分工文档一致；`POST /donations` C 代写、B 验收 | 已决 |
| Q-B-07 | 校园点位与平面图底图资源：区域级 10—20 个点位的清单、平面图图片谁提供 | 没有底图，P06"点击平面图选点"无法实现，只能降级下拉 | 先按下拉选点上线，底图到位后再替换；点位清单由社团提供初稿 | 中 |
| Q-B-08 | 字典与种子数据初值：花色、支出用途、合作医院报价、物种名录由谁给 | 没有种子数据，建档与支出页下拉为空，演示看不出效果 | 已按文档假设落 48 条字典、10 个物种、3 家示例医院、12 个点位；医院为虚构示例、保护级别待核，见《数据库种子数据清单》 | 已决（可替换） |
| Q-B-09 | 认养金额档位与上限（文档假设 A-01：月 30／50、季 90／150；A-02：每人 ≤2 只）是否定稿；资金收款主体与公示口径（需求 Q-01、Q-03）是否已与社团确认 | 影响认养方案种子、资金口径与公示文案；合规表述若错，项目可信度受损 | 按 A-01／A-02 落种子；收款主体沿用"社团名义收取、学校备案"表述，页面不写"公开募捐" | 中 |
| Q-B-10 | 演示与部署环境：本机演示还是云主机；是否有 2C4G；是否允许装 Docker | 决定 W4 交付形态（compose 还是单机 jar） | 优先本机 Docker Compose，另备 jar + sql 离线包 | 中 |
| Q-B-11 | 日志与脱敏的归属：B 的业务代码是直写 `sys_log`，还是统一调 A 的日志切面；字段级脱敏注解由谁提供、B 的 DTO 是否统一继承 | 若两处各写一份，日志会出现两套格式，答辩追问时说不清 | 统一走 A 的 `@OpLog` 切面 + `@Sensitive` 注解，B 只标注不实现 | 中 |
| Q-B-12 | 金额与时间的序列化约定：接口文档定"金额单位为分、ISO-8601 带时区"，前端请求层是否已按此锁定 | 单位错一位，全站金额差 100 倍；时区错会让"24h 超时"判断偏 8 小时 | 由 A 在请求层统一转换，B 的 DTO 只输出分与 ISO-8601，联调首日对一次样例 | 中 |

## 11 建议的下一步（等你确认后执行）

1. 先答 Q-B-01／02／04／05 四项——它们分别卡住排期、第一行代码、建表、协作方式。
2. 我按确认结果补齐 W0 的四件事：ADR-001、`V1__init.sql`、统一响应体骨架、接口归属对照表。
3. 同时提醒队友同步重写后的历史（`git fetch && git reset --hard origin/main`），避免分叉。

## 12 执行进度（2026-11-21）

| 任务 | 状态 | 证据 |
|---|---|---|
| T0.1 技术栈 ADR | 完成 | `docs/adr/ADR-001-backend-stack.md`：Boot 3.5.16 / MyBatis-Plus 3.5.18.1 / MySQL 8.4 / Redis / `com.just.critter` / HTTP 映射 / 30505+40108 |
| T0.2 统一响应体、异常映射、requestId | 完成 | `support/api`、`support/exception`，`ApiContractTest` 4 例 |
| T0.3 Flyway 建表 | 完成 | `V1__init.sql`：37 张业务表 + `seq_no` + 3 视图；`MySqlSchemaIT` 5/5 通过（真实 MySQL 8.4） |
| T0.4 枚举常量与字典种子 | 完成 | 新增 `Gender`/`ReportType`/`DonationStatus`/`ExpenseStatus`/`OccurrenceScale`/`HealthType`/`ProjectTargetType`；`V5__seed_reference_data.sql` 落 48 条字典、10 物种、3 示例医院、12 点位；`DomainEnumCatalogTest` 7 例、`SeedReferenceDataIT` 4/4 |
| T1.1 编号器 / T1.3 状态机 | 完成 | `AnimalCodeGeneratorTest` 5 例、状态机 25 例；真库发号走 `seq_no` 行锁 |
| T1.2 档案 CRUD | 完成（一期） | `POST/GET/PUT /animals`、`GET /animals/{code}/history`；建档发号、五条件筛选、编辑留痕、公开详情裁剪；`AnimalServiceTest` 8 例、`AnimalControllerTest` 5 例、`AnimalArchiveIT` 3 例 |
| T1.4 健康与防疫 | 完成（一期） | `GET/POST /animals/{code}/health`；记录日期不早于建档日、计划日期不早于记录日、疫苗三天内到期提醒；`HealthServiceTest` 6 例、`HealthControllerTest` 4 例、`HealthRecordIT` 2 例 |
| T1.5 群体与基础数据 | 完成（P0） | 群体建档/列表/详情/编辑、目击登记与列表、物种/点位只读、出现记录；规模区间 30504、JSON 点位、季节筛选；`GroupServiceTest` 8 例、`GroupControllerTest` 7 例、`BiodiversityServiceTest` 5 例、`BiodiversityControllerTest` 5 例、`GroupAndBiodiversityIT` 3 例 |
| W1 档案纵切 | 完成 | 个体档案、健康、群体、目击、出现记录、基础数据只读全部可联调 |
| C-1～C-5 契约对齐 | 完成 | 全量 63 例绿，已推送 `7733456` |
| D1 合并集成 | 完成（本地） | 去重 POM/`MybatisPlusInterceptor`；`AuthInterceptor` 增加匿名只读白名单；档案域 4 个控制器改用 `AuthContext` + `@RequireRoles`；迁移统一为 V1→V2 身份→V3 统计→V4 去重→V5 基础数据 |
| T2.1 学生上报 | 完成（一期；30503 待接口补物种字段） | `POST /reports`：五类枚举、点位、至少 1 张照片、描述 ≥10 字；RP 当日发号、24 小时时限；同点位 24 小时内未关闭上报返回 `duplicated/duplicateOf`；合并 main 后迁移重排为 V8 补 `animal_id/contact`、V9 字典兼容、V10 规范类型归并；`ReportCodeGeneratorTest` 3 例、`ReportServiceTest` 6 例、`ReportControllerTest` 2 例、`ReportIT` 2 例 |
| T2.2 上报管理与受理 | 完成（一期；工单看板/流转下一轮） | `GET /reports` 状态/类型/点位/时限筛选、`GET /reports/{id}` 联系方式按角色脱敏、`POST /reports/{id}/accept` 复用上报时生成的 PENDING 工单并按 `ACCEPT` 迁移为 PROCESSING、`PUT /reports/{id}/close` 关闭无效上报；工单号 RO 当日发号、截止时间沿用上报 +24h；`OrderCodeGeneratorTest` 3 例、`RescueOrderServiceTest` 3 例、`ReportServiceTest` 10 例、`ReportControllerTest` 6 例、`ReportIT` 4 例 |
| T2.3 工单看板与流转 | 完成（含超时扫描） | `GET /orders` 状态/类型/超时筛选、`GET /orders/{no}` 来源上报与 sys_log 时间线、`PUT /orders/{no}/status` 表驱动流转；`OrderTimeoutJob` 每小时扫描 PENDING 且 `deadline_at < now` 的工单，经 `PENDING --TIMEOUT--> TIMEOUT` 条件更新并写 `ORDER_TIMEOUT` 审计，多实例幂等；TIMEOUT 工单仍可被受理并复用为 PROCESSING；`OrderServiceTest` 7 例、`OrderControllerTest` 4 例、`OrderIT` 2 例 |
| T2.4 项目发起与审核 | 完成（一期） | `POST /projects` 草稿/直接提交、`GET /projects` 公开列表、`GET /projects/{no}` 公开详情、`PUT /projects/{no}` 修改提交、`PUT /projects/{no}/audit` 审核；预算合计 > 目标返回 `40101` 并带差额；公开页只展示 FUNDING 及之后状态；`ProjectCodeGeneratorTest` 2 例、`ProjectServiceTest` 9 例、`ProjectControllerTest` 8 例、`ProjectIT` 4 例；T2.4 说明见 `docs/T2.4-项目发起与审核说明.md` |
| T3.1 捐赠登记与核对入账 | 完成（一期） | `POST /donations` 登记固定 `PENDING_VERIFY`；`GET /donations` 管理员按状态/项目筛选；`PUT /donations/{no}/confirm` 单笔核对；`PUT /donations/batch` 逐笔独立事务批量核对；金额一致才写 `VERIFIED` 并同事务递增 `project.raised_amount`，金额不符转 `ABNORMAL` 且不动进度；项目已筹与已入账合计不一致返回 `40106`；`DonationCodeGeneratorTest` 2 例、`DonationServiceTest` 10 例、`DonationControllerTest` 5 例、`DonationIT` 6 例；T3.1 说明见 `docs/T3.1-捐赠登记与核对入账说明.md` |

测试基线：`mvn -B test` **400 例全绿**（2026-11-21 收尾批次，含 D3 通知/就诊与项目到期任务）；真库测试另计 **50 条**（含本轮 6 条并发/事务用例，默认跳过），需 `CRITTER_MYSQL_IT=true` 且 `CRITTER_MYSQL_PASSWORD` 指向本地 root 口令才运行，默认跳过；IT 统一重建 `critter_codex_it`。迁移为 V1→V18，`critter_codex` 已应用至 V18。

T1.2 遗留：`GET /animals/{code}/history` 已加管理员拦截；写操作统一走 `AuthContext.require()`，角色用 `@RequireRoles`。`photos` 只存首张为 `cover_file_id`，其余待 `sys_file` 关联表确定后再落。

T1.4 遗留：提醒目前只在健康记录上回 `reminderDue` / `daysUntilNext` 供前端角标，真正投递站内消息要等 C 的消息通道（`POST /messages` 或事件订阅），B 不直接写 `message` 表；`GET /animals/{code}/health` 登录可见、`POST` 管理员，鉴权已统一走 `AuthContext`。

T1.5 遗留：归并/拆分、目击归属修正、群体状态接口、物种/点位写接口均为 P1；`30501 群体不可认养` 需 C 在认养提交处拦截，B 只保证群体编号可识别；目击归属状态暂用字符串 `ASSIGNED/UNASSIGNED`；鉴权同前。

Q-B-04 已闭环：本机 MySQL 8.4，`root/1234`，`127.0.0.1:3306`；`critter_codex` 主库（V18），`critter_codex_dev` 联调库，`critter_codex_it` 真库 IT 专用库，`critter_ro@localhost` 只读，详见《联调环境说明》。

## 13 本轮治理结果（2026-11-21）

| 项 | 结果 |
|---|---|
| 分支策略 | 本轮起走 `feature/*`；当前 `feature/d2-w0-seed` |
| main 保护 | 已开启：要求 PR、禁止强推、禁止删除分支、管理员同样受限；批准数暂设 0，等 A/C 开始提 PR 后改 1 |
| 接口归属 | 《接口归属对照表》118 条，A 31／B 52／C 35，与分工说明书一致 |
| 联调库 | `critter_codex_dev` 已建并迁移；只读账号 `critter_ro@localhost`，写操作被 MySQL 拒绝（1142） |
| 种子数据 | 48 字典项、10 物种、3 示例医院、12 点位；均标注示例/待核，替换方式见《数据库种子数据清单》 |

剩余待确认：Q-B-01 答辩日期、Q-B-03 人数、Q-B-07 平面图底图、Q-B-09 认养档位、Q-B-10 演示环境、Q-B-11 日志归属、Q-B-12 序列化约定。

## 14 本轮后端闭环完成（2026-11-21）

分支 `feature/b-remaining-p0`（基点 `d595e2e`），目标是把 P0/P1 剩余业务闭环一次补齐，并把前端已建页面接到真实接口。

### 14.1 交付内容

| 闭环 | 后端 | 前端 | 关键规则 |
|---|---|---|---|
| 动物状态迁移 | `PUT /animals/{code}/status`、`GET /animals/{code}/status-history`；`AnimalStatusService` 从状态机迁移表反查事件，独立于普通字段编辑；`AnimalDetail.allowedStatuses` | `AnimalStatusDialog.vue`、`AnimalDetailView` 状态按钮与时间线，仅 `ANIMAL_ADMIN` 可见 | 原因 ≥5 字（30401）；非法目标 30402 且返回 `current/target/allowed`；`RELEASE` 需健康记录含“绝育/TNR”，否则 30603 |
| 支出登记与复核 | `POST /expenses`、`GET /expenses`、`PUT /expenses/{expenseNo}/review`；项目行锁后算 `balance=raised−approved` 与 `available=balance−pending` | `ExpenseCreateDialog.vue`、`ExpenseReviewDialog.vue`、重写 `ExpensesView.vue` | 余额不足 40103 并返回 `projectBalance/reserved/requested/gap`；自复核 40104；单笔 ≤50000 分一签、>50000 分两签且第二签不得与第一签同人；仅第二签落 `APPROVED` 并更新项目 `spent_amount` |
| 逐笔台账 | `GET /ledger?projectNo&from&to`；收入只取 VERIFIED 捐赠的 `verifiedAt`，支出只取 APPROVED 支出的 `occurredAt`，按时间倒序合并；汇总用 `long` 分 | 重写 `LedgerView.vue`，日期/项目/方向筛选、逐笔流水与合计 | 台账仅管理员（`ANIMAL_ADMIN`/`SYSTEM_ADMIN`）只读；待核对/待复核不入账；`balance=income−expense` |
| 我的上报 | `GET /reports/mine?page&size`，按 `reporterId` 过滤并复用管理员列表映射 | 重写 `MyReportsView.vue` 为服务端分页 + 详情抽屉；删除 `reportReceipts.ts` 本地回执 | 学生只能看自己的上报；详情仍按 id 走 `GET /reports/{id}` |
| 工单超时 | `@EnableScheduling` + `OrderTimeoutJob`，每小时扫描；上报时即生成 PENDING 工单，受理时复用并按 `ACCEPT` 迁移 | 工单看板沿用超时高亮 | 条件更新 `status=PENDING AND deadline_at<now` 保证多实例幂等；写 `ORDER_TIMEOUT` 审计；TIMEOUT 仍可受理 |
| 项目编辑与结项 | `PUT /projects/{no}` 支持 DRAFT/REJECTED；`PUT /projects/{no}/settle`、`GET /projects/{no}/settlement`；`ProjectSettlementService` | `ProjectCreateDialog` 新建/编辑双模式、`SettlementCreateDialog.vue`、`SettlementView.vue`、`ProjectDetailView` 入口 | 报告 20–10000 字、结余去向必填、附件 ≤10；有未复核支出 40105；FUNDING 达标走 `TARGET_REACHED`，未达标到期走 `DEADLINE_REACHED`，再 `SETTLE`；公示公开只读 |
| 群体/物种补全 | `PUT /groups/{code}`、`POST /groups/{code}/sightings`、`GET /sightings`、`POST /occurrences`（写接口限管理员） | `GroupCreateDialog` 双模式、`SightingDialog.vue` 目击时间线、`OccurrenceCreateDialog.vue`、`SpeciesView` 入口 | 目击点位必须是该群体活动点位；出现记录必须有照片；群体不可认养（30501） |

### 14.2 本轮发现的缺陷与修复

| 缺陷 | 现象 | 修复 |
|---|---|---|
| `ck_expense_double_sign` 阶段错误 | `V1` 约束要求大额支出在 INSERT 时就具备两名复核人，导致 >500 元支出永远无法登记 | 新增 `V14__expense_double_sign_stage.sql`：仅 `APPROVED` 状态要求双签，登记与第一签阶段允许为空 |
| 角色集合比较错误 | `ProjectSettlementService` 用 `Set<String>.contains(Role.ANIMAL_ADMIN)`，枚举与字符串恒不相等，管理员提交结项也报 20003 | 改为 `CurrentUser.hasRole("ANIMAL_ADMIN")` |
| 工单无 PENDING 候选 | 受理时直接建 PROCESSING 工单，超时扫描无对象 | 上报时创建 PENDING 工单；受理复用 PENDING/TIMEOUT 工单经状态机迁移，缺失时才补建 |
| 台账访问口径不一致 | 后端曾按公开只读放行，前端只允许管理员 | 统一为管理员只读（`@RequireRoles`），从匿名白名单移除 `/ledger` |

### 14.3 验证记录

| 验证项 | 结果 |
|---|---|
| 后端全量测试 | `mvn -B test`：400 例，0 失败，0 错误，0 跳过，BUILD SUCCESS |
| 前端构建 | `npm run build`：`vue-tsc -b` 无类型错误，1825 modules，构建成功 |
| 动物状态 | `TEST-CAT-001`：`ON_CAMPUS→TREATING` 成功，历史返回“在校园活动 → 治疗中”，事件 `REPORT_ACCEPTED` |
| 支出双签 | 60000 分登记成功→自复核 40104→第一签 `FIRST_APPROVED`→第二签 `APPROVED`；项目 `spentAmount=60000`，台账支出 60000 |
| 台账/结项 | `PRJ-TEST-001` 结项成功，项目 `SETTLED`、公示收支一致；匿名 `GET /projects`、`GET /projects/{no}`、`GET /projects/{no}/settlement` 均可读；匿名 `/ledger` 被拒 |
| 我的上报 | 学生上报后 `GET /reports/mine` 返回本人 1 条；管理员受理后复用同一 PENDING 工单为 PROCESSING |
| 工单超时 | 回填 `deadline_at` 后扫描任务将 PENDING 置为 TIMEOUT 并写 `ORDER_TIMEOUT` 审计；TIMEOUT 工单再受理成功 |
| 群体/物种 | 新建群体→编辑特征/规模→登记目击（列表 1 条）→登记物种出现记录（列表 1 条）全部成功 |

### 14.4 收尾批次（`feature/b-final-polish`）

| 编号 | 事项 | 状态 | 证据 |
|---|---|---|---|
| T4.1 | 并发与事务用例 ≥6 条 | 完成 | `ConcurrencyAndTransactionIT` 6/6；`docs/T4.1-并发与事务验证记录.md` |
| T4.2 | Compose、备份恢复、匿名公示入口 | 完成（Docker 未在本机执行） | `deploy/docker-compose.yml`、`backup-mysql.ps1`、`restore-mysql.ps1`、`deploy/README.md`；恢复演练 200/12/1200/5 |
| T4.3 | 演示数据集 | 完成 | `critter-backend/demo/qywl-demo.sql`、`generate_demo_data.py`、`reset-demo.ps1`、`demo/README.md` |
| T4.4 | 4 分钟答辩脚本 | 完成 | `docs/T4.4-四分钟答辩脚本.md` |
| — | 项目到期自动流转 | 完成 | `ProjectDeadlineJob`：`FUNDING` 且 `end_date < today`，达标转 `PENDING_SETTLE`，未达标转 `ENDED`，条件更新 + 审计；`ProjectDeadlineJobTest` 5/5 |

## 15 收尾批次完成（2026-11-21）

分支 `feature/b-final-polish`，从最新 `origin/main`（`10c8450`）创建。

### 15.1 演示数据规模

| 实体 | 数量 | 关键分布 |
|---|---:|---|
| 动物 | 200 | ON_CAMPUS 148、TREATING 15、ADOPTED 12、RELEASED 5、DECEASED 3 等 |
| 项目 | 12 | FUNDING/SETTLED/PENDING_SETTLE/ENDED/DRAFT/PENDING_AUDIT |
| 捐赠 | 1200 | VERIFIED 900、PENDING_VERIFY 250、ABNORMAL 50 |
| 群体 | 5 | 含目击 50、物种出现记录 30 |
| 支出 | 45 | APPROVED 27、FIRST_APPROVED 9、PENDING_REVIEW 5、REJECTED 4 |
| 上报/工单 | 60/60 | 工单含 PENDING 15、PROCESSING 10、TO_REVISIT 5、CLOSED 20、TIMEOUT 10 |
| 认养 | 20 | ACTIVE 12、EXPIRED 4、PENDING_PAYMENT 2 等 |
| 健康/状态日志 | 160/52 | 另有就诊 10、TNR 20、公告 10、消息 30、任务 8、报名 20 |

资金恒等式：`project.raised_amount` 与 `VERIFIED` 捐赠合计一致，`project.spent_amount` 与 `APPROVED` 支出合计一致，核对结果 `fund_mismatch=0`。SQL 已分别导入 `critter_codex_dev` 与 `critter_codex`。

### 15.2 本轮修复

| 缺陷 | 根因 | 修复 |
|---|---|---|
| 发号器并发首取死锁 | `INSERT IGNORE` 插入意向锁与 `SELECT ... FOR UPDATE` 交叉 | 改为单条 `INSERT ... ON DUPLICATE KEY UPDATE current_no = LAST_INSERT_ID(current_no + 1)` + `SELECT LAST_INSERT_ID()` |
| 到期任务状态迁移报错 | `ProjectDeadlineJob` 传 `Role.SYSTEM`，项目迁移只允许 `ADMIN` | 仅到期两条迁移改用 `ADMIN_OR_JOB`，新增 `projectJob()` 构造 |
| 联调库 `message.type` 过窄 | 旧库仍为 `VARCHAR(16)`，`DONATION_PROGRESS` 长 17 | 手工扩到 `VARCHAR(24)`，与最新迁移一致 |
| 主库 Flyway V2 残留 | 旧分支历史中 V2 已被 V11 取代 | 按 repair 语义将 V2 行 `type='DELETE'`；V16/V18 以一次性 out-of-order 补齐，主库最终 V18 |

### 15.3 验证记录

| 验证项 | 结果 |
|---|---|
| 后端定向测试 | `ProjectDeadlineJobTest` 5/5；`ConcurrencyAndTransactionIT` 6/6 |
| 前端构建 | `npm run build`：`vue-tsc -b` 通过，1853 modules |
| 公开接口 | `GET /projects` 匿名返回 10 个公开项目；`GET /animals` total=200；`GET /ledger?projectNo=PRJ-2026-009` income=4,284,624 / expense=170,000 / entries=93 |
| 匿名公示 | `GET /projects/PRJ-2026-009/settlement` 匿名成功；前端路由 `meta.public=true` |
| 备份恢复 | 备份 0.44 MB；恢复演练库后 animal/project/donation/animal_group = 200/12/1200/5 |
| 最新后端 | `http://127.0.0.1:8083`（主库，PID 38764 为本轮重启进程；公开项目 10、动物 200、台账 income=4,284,624 / expense=170,000 / entries=93） |

### 15.4 仍未完成

| 事项 | 说明 |
|---|---|
| 校园真实底图 | 仍为示意点位图，等 Q-B-07 底图资产 |
| 移动端适配 | 当前按桌面端验收，移动端为后续迭代 |
| Docker 实跑 | 本机未安装 Docker；Compose/Dockerfile 已编写，未在本机执行 |

### 15.5 后续增强接口交付记录（2026-11-21）

本轮补齐 D2 域剩余增强接口，并修复三项行为缺口。迁移为 `V20__p1_merge_alias_and_extensions.sql`（V19 已被 C 的志愿任务迁移占用），主库目标状态为 V20（42 张业务表 + `seq_no` = 43 张表）。

| 分组 | 接口/规则 | 交付要点 |
|---|---|---|
| 档案治理 | `POST /animals/{code}/merge`、`GET /animals/{code}/qrcode`、`PUT /sightings/{id}` | 副档保留并写别名、旧编号全链路可解析；二维码 300×300 PNG；目击可清空/改归属，已消失群体拒绝 30505 |
| 群体维护 | `POST /groups/merge`、`POST /groups/{code}/split` | 仅同物种合并；副群体置 GONE 并写 `mergedIntoCode`；拆分生成新编号、源群体缩区间、目击可迁移 |
| 基础数据 | `POST/PUT /points`、`POST/PUT /species` | 点位停用不删除、坐标 0–100、名称/俗名唯一；术语与保护级别可维护 |
| 资金 | `GET /ledger/export` 及任务状态/下载、`PUT /donations/{no}/flag`、`GET /donations/{no}/certificate` | 台账 ≤5 万行同步 xlsx、超阈值异步；已入账捐赠必须先冲正才能标异常；证书仅 VERIFIED 可签发，PDFBox 横版 A4 |
| 公示 | `POST /projects/{projectNo}/settlement/notes` | 只能追加说明，不能改正文，`appendNotes` 随详情返回 |
| 行为缺口 | 野猪拦截、疫苗提醒、公示追加说明 | 上报可带 `speciesId`；匹配野猪返回 30503 且不落单；疫苗到期前 3 天按录入人投递并按记录 id 去重 |
| 编号器 | `seq_no` 取号 | 修复新建库首次 `LAST_INSERT_ID()` 返回行号导致首号回退；改为同事务读回 `current_no` |

**验证**：后端全量 431 个单测通过；真库 51 个 IT 通过（含 V20 / 43 表断言）；前端 `npm run build` 通过。

## 16 P2 缺口清零（2026-11-23）

对照 2026-11-23 审计的 12 个缺口（11 个部分完成 + 1 个未完成 FR-R-05），本轮把“后端规则 + 数据库迁移 + 前端入口 + 可验证测试”全部补齐。迁移 `V21__p2_report_confirm_and_patrol.sql` 已应用主库，版本 v21；主库 44 张业务表（含 `seq_no`，不含 `flyway_schema_history`）。

| 缺口 | 后端 | 前端入口 | 验证 |
|---|---|---|---|
| 点位管理 | `GET /points/admin`、`DELETE /points/{id}`；六类引用统计，有引用 30413 | 档案中心“点位维护”页签 | `BaseDataAdminServiceTest` 新增 2 例 |
| 二维码公开页 | 二维码前缀改 `/p/`；公开详情复用 `GET /animals/{code}` | `PublicAnimalView` + 详情“二维码贴牌” | `AnimalQrCodeServiceTest` |
| 档案合并 UI | 合并口径沿用 V20 | 详情“合并重复档案”；旧编号提示 `mergedCodes` | `AnimalMergeServiceTest`、`AnimalArchiveIT` |
| 待归属目击 | `POST /sightings` 生成 `UNASSIGNED` | 群体页“登记待归属目击”与指派表格 | `GroupServiceTest` 新增 1 例、`GroupMaintenanceServiceTest` |
| 群体合并/拆分/旧编号 | `GroupDetail` 增 `queriedCode/mergedFrom/splitFrom/mergedIntoCode` | 详情“合并”“拆分”、旧编号跳主档 | `GroupMaintenanceServiceTest` |
| 物种维护 UI | `POST/PUT /species` 已有 | 物种名录新增/编辑弹窗 | `BaseDataAdminServiceTest` |
| 结案确认 | 7 天确认窗口、`PUT /reports/{id}/confirm`、`ReportConfirmJob` 自动确认 | 我的上报待确认提示与确认按钮 | `ReportServiceTest` 新增 4 例、`ReportConfirmJobTest` 2 例 |
| 无效上报 | `close` 支持 `invalid`，统计排除 | 关闭弹窗“标记无效上报”，队列显示无效/待确认 | `ReportControllerTest`、`ReportServiceTest` |
| 结项追加说明 UI | `POST /settlement/notes` 已有 | 结项公示页追加说明列表/表单 | `ProjectSettlementNoteTest` |
| 台账导出 UI | 同步/异步导出已有 | 台账页“导出台账” | `LedgerExportServiceTest` |
| 捐赠异常/凭证 UI | `flag`/`certificate`/`mine` 已有 | 捐赠面板、个人中心“我的捐赠” | `DonationServiceTest`、`DonationIT` |
| 巡查记录 | `patrol_record`、`GET/POST /patrols`、`POST /patrols/{id}/convert` | 档案中心“巡查记录”页签 | `PatrolServiceTest` 5 例 |

**本轮修复**：真库 IT 发现 `AnimalAliasMapper.selectAliasCodesByAnimalId` 按不存在的 `id` 列排序；`animal_alias` 的主键是 `alias_code`，改为 `ORDER BY created_at, alias_code` 后通过。

**验证**：`mvn -B -q test` 516 个用例 0 失败；`CRITTER_MYSQL_IT=true mvn -B -q -Dtest=*IT test` 52 个用例 0 失败；`npm run build` 通过。
