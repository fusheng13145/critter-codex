# 成员 B 前端页面实现说明

> 范围：P02 档案中心、P04 资金、P06 上报与工单  
> 技术栈：Vue 3.5 + TypeScript + Element Plus + Pinia + vue-router  
> 前端首版分支：`feature/b-frontend-pages`（基于远端 main `bba4211`）
>
> 后端闭环接入分支：`feature/b-remaining-p0`（基于 main `d595e2e`，见 §9）

## 1. 页面范围与优先级

| 优先级 | 页面 | 路由 | 交付口径 |
| --- | --- | --- | --- |
| P0 | 个体档案列表 | `/animals` | 档案主入口；筛选、分页、管理员新建 |
| P0 | 个体档案详情 | `/animals/:id` | 档案概览、照护坐标、健康记录、字段变更历史；管理员编辑 |
| P1 | 上报入口 / 上报处理 | `/report` | 学生提交与管理员队列同一路由，用页签切换 |
| P1 | 工单看板 | `/orders` | 队列筛选、详情、时间线；按后端允许状态流转 |
| P1 | 募捐项目列表 | `/projects` | 项目卡、进度、剩余天数；管理员发起项目 |
| P1 | 项目详情 | `/projects/:id` | 预算、已入账捐赠、审核、捐赠登记与管理员核对 |
| P2 | 群体档案 | `/groups` | 群体筛选、区间规模、详情抽屉；管理员新建 |
| P2 | 物种名录 | `/species` | 物种卡片、筛选、关联群体与出现记录抽屉 |
| P2 | 资金台账 | `/ledger` | 已接 `GET /ledger`：日期/项目/方向筛选、逐笔流水与合计；管理员只读 |
| P2 | 支出复核 | `/expenses` | 已接 `POST/GET /expenses` 与 `PUT /expenses/{no}/review`：登记、筛选、分页、双签复核 |
| P2 | 我的上报 | `/report/mine` | 已接 `GET /reports/mine`：服务端分页、详情抽屉，不再依赖本机回执 |

## 2. 路由与菜单接入

| 路由 | 视图 | meta.menu | 说明 |
| --- | --- | --- | --- |
| `/animals` | `AnimalsView` | `archive` | 个体列表 |
| `/animals/:id` | `AnimalDetailView` | `archive` | 动态详情 |
| `/groups` | `GroupsView` | `archive` | 复用档案菜单 |
| `/species` | `SpeciesView` | `archive` | 复用档案菜单 |
| `/fund` | 重定向到 `projects` | `fund` | 后端菜单仍下发 `/fund`，保留兼容入口 |
| `/projects` | `ProjectsView` | `fund` | 项目列表 |
| `/projects/:id` | `ProjectDetailView` | `fund` | 动态详情 |
| `/ledger` | `LedgerView` | `fund` | 资金台账 |
| `/expenses` | `ExpensesView` | `fund` | 支出复核 |
| `/report` | `ReportView` | `report` | 提交上报 / 上报处理 |
| `/report/mine` | `MyReportsView` | `report` | 本人上报服务端列表 |
| `/orders` | `OrdersView` | `report` | 工单看板 |

- 路由 meta 继续使用 `title`、`menu`、`public`、`owner`，没有另建权限体系。
- `AppLayout` 的菜单高亮改为优先读取 `route.meta.menu`，解决 `/groups` 无法点亮“生物档案”、`/projects` 无法点亮“资金透明”的问题。
- `/fund` 只做重定向，不复制项目页，避免两份实现漂移。

## 3. 已实现页面行为

### P02 档案中心

- 个体列表：关键词、状态、点位、绝育、认养筛选；原生表格分页；封面通过鉴权图片加载；管理员显示“新建档案”。
- 个体详情：基本档案、类别/性别/毛色/估计年龄、点位与坐标、健康记录时间线、管理员字段级变更历史。
- 新建档案：类别、性别、毛色、估计年龄、点位、识别特征、照片、初步体检备注；校验照片至少 1 张、特征至少 5 字。
- 编辑档案：管理员可改基础字段与内部备注；变更原因必填且至少 5 字；毛色取字典 label 值，匹配后端自由文本 `color`。
- 健康记录：疫苗、驱虫、体重、病史四类；记录时间、结果、下次日期；疫苗临期在时间线标记提醒。
- 群体档案：物种、状态、点位筛选；规模以闭区间展示，不把中位数当事实；详情抽屉；管理员新建群体，点位多选并校验上下限。
- 物种名录：物种卡片网格；关键词与类群筛选；点击卡片后按 `speciesId` 并行拉取关联群体和出现记录。

### P04 资金

- 项目列表：状态、对象类型、关键词筛选；项目卡展示目标、已筹、已支出、进度、剩余天数、已入账笔数。
- 项目详情：预算明细、已入账捐赠、审核意见；管理员按 `allowedActions` 显示提交审核、审核通过/驳回；仅 `FUNDING` 项目开放“我要捐赠”。
- 发起项目：名称、用途、对象类型、目标金额、起止日期、预算明细、封面；金额统一按整数分提交。
- 捐赠登记：金额、匿名、留言、凭证；提交只生成 `PENDING_VERIFY`，页面明确提示“登记不等于入账”。
- 管理员核对：单笔可填实收金额、到账时间、备注；金额不一致转 `ABNORMAL`；批量核对按登记金额入账，逐条回显失败项；凭证可预览。
- 资金台账：接 `GET /ledger`，按日期、项目、方向筛选；收入只显示 VERIFIED 捐赠、支出只显示 APPROVED 支出，页面合计与后端 `summary` 一致，不再拼假流水。
- 支出复核：接 `GET /expenses` 列表、`POST /expenses` 登记、`PUT /expenses/{expenseNo}/review` 复核；余额不足回显差额，登记人自我复核前置拦截，>500 元显示“待二签”，复核弹窗可预览票据。
- 项目编辑与结项：草稿/驳回状态可重新编辑；`PENDING_SETTLE` 可提交结项报告、结余去向与附件；结项公示页只读展示收支与报告。
- 群体与物种：群体详情支持编辑与登记目击，目击时间线展示时间、点位、数量、行为与照片；物种详情支持登记出现记录。

### P06 上报与工单

- 提交上报：五类枚举、示意点位图选点、最多 3 张照片、描述至少 10 字、关联档案与联系方式可选。
- 提交结果：展示上报编号、受理截止、疑似重复提示；本次提交在当前页面展示回执，正式记录统一在“我的上报”查询。
- 上报处理：管理员可切换“提交上报/上报处理”；队列默认待受理，支持状态、类型、超时筛选；详情抽屉可看照片与联系方式；`ANIMAL_ADMIN` 可受理复用 PENDING 工单或关闭上报。
- 我的上报：读取 `GET /reports/mine` 服务端列表，支持分页与详情抽屉；不再写入 `localStorage`，清理缓存或换设备不会丢失。
- 工单看板：状态、类型、超时筛选；详情抽屉展示工单信息、现场照片、处置记录与时间线；只渲染后端 `allowed` 中的目标状态；`ANIMAL_ADMIN` 可流转，`SYSTEM_ADMIN` 只读。

## 4. 共享能力与数据口径

### API 封装

| 文件 | 覆盖接口 |
| --- | --- |
| `src/api/archive.ts` | 动物、群体、物种、点位、出现记录、健康记录、变更历史 |
| `src/api/fund.ts` | 项目、预算、捐赠登记、捐赠核对、批量核对 |
| `src/api/rescue.ts` | 上报、受理、关闭、工单、状态流转 |

### 共享组件

| 组件 | 用途 |
| --- | --- |
| `SectionTabs` | 二级导航；显式 `flex-direction: row`，避免被全局 `nav` 竖排规则命中 |
| `StatusTag` | 业务状态标签；颜色只表达风险，不把正常状态涂成告警色 |
| `PageState` | 加载 / 错误 / 空态三态收口 |
| `AuthImage` | 用 axios Blob 带 Authorization 读取 `/files/{id}/content` |
| `PhotoGallery` | 附件缩略图与大图预览 |
| `PhotoUploader` | 选文件 → `uploadFile` → fileId 数组；支持图片/PDF、数量与大小限制 |
| `CampusPointMap` | 0–100 相对坐标示意点位图；标注“底图待校准” |

### 关键口径

- 金额：前端输入按元，提交前转整数分；展示统一 `centsToYuan`。
- 文件：`AnimalSummary.coverUrl` 是 `/api/v1/files/{fileId}` 元信息地址，不是纯 fileId；`fileIdFromUrl()` 负责剥离。
- 状态：上报 `PENDING/PROCESSING/CLOSED`；工单 `PENDING/PROCESSING/TO_REVISIT/TIMEOUT/CLOSED`；项目 `DRAFT/PENDING_AUDIT/FUNDING/PENDING_SETTLE/SETTLED/ENDED/REJECTED`；捐赠 `PENDING_VERIFY/VERIFIED/ABNORMAL`。
- 行为：上报“已提交”不等于“已受理”；捐赠“已登记”不等于“已入账”；工单超时只是风险标记，不改业务状态。
- 权限：受理、关闭上报、工单流转只给 `ANIMAL_ADMIN`；`SYSTEM_ADMIN` 可看不可操作；页面不自行推断权限，管理动作以接口下发的 `allowedActions` / `allowed` 为准。

## 5. 文件清单

### 修改（3 个）

- `critter-frontend/src/api/types.ts`：追加 D2 域类型，字段对齐后端 service record，金额为整数分。
- `critter-frontend/src/router/index.ts`：新增 P02/P04/P06 路由与 `/fund` 重定向，补 meta。
- `critter-frontend/src/layouts/AppLayout.vue`：菜单高亮优先使用 `route.meta.menu`。

### 新增（34 个）

- API：`src/api/archive.ts`、`src/api/fund.ts`、`src/api/rescue.ts`
- 共享组件：`src/components/AuthImage.vue`、`CampusPointMap.vue`、`PageState.vue`、`PhotoGallery.vue`、`PhotoUploader.vue`、`SectionTabs.vue`、`StatusTag.vue`
- 工具：`src/utils/domain.ts`
- P02：`src/views/archive/` 下列表、详情、群体、物种及对话框/面板；本轮新增 `AnimalStatusDialog.vue`、`SightingDialog.vue`、`OccurrenceCreateDialog.vue`
- P04：`src/views/fund/` 下项目列表/详情、台账、支出、项目卡及对话框；本轮新增 `ExpenseCreateDialog.vue`、`ExpenseReviewDialog.vue`、`SettlementCreateDialog.vue`、`SettlementView.vue`
- P06：`src/views/rescue/` 下上报页、我的上报、工单、表单、队列；本轮删除 `src/utils/reportReceipts.ts`

## 6. 验证记录

### 构建

```text
cd critter-frontend
npm run build
```

结果：`vue-tsc -b` 无类型错误，Vite 1843 modules transformed，构建成功。仅保留项目原有的“大 chunk > 500 kB”警告，未为此引入新依赖。

### 浏览器走查

本机 8080/5173 被另一个课程项目占用，走查时临时使用：

- 前端：`http://localhost:5175`
- 后端：`http://localhost:8081`
- 账号：`2023010101 / Admin@2026`（`ANIMAL_ADMIN`）
- CORS：后端启动参数临时放行 `http://localhost:5175`，未改后端代码

已完成的真实交互闭环（走查后临时数据已从开发库与本地文件清理，未写入仓库）：

1. 新建个体档案（上传封面）→ 列表出现 → 详情 → 编辑（填变更原因）→ 变更历史出现记录 → 登记健康记录 → 健康时间线刷新。
2. 发起项目 → 管理员审核通过 → 项目转 `FUNDING` → 登记捐赠（上传凭证）→ 管理员单笔核对 → 已筹金额与进度刷新 → 台账汇总与月度收支同步。
3. 提交上报（选点、照片、描述）→ 回执展示编号/截止/重复提示 → 管理员队列受理并生成工单 → 工单详情 → 流转到待回访，时间线与处置记录刷新。
4. 空态：无档案、无群体、无项目、无工单时均显示明确空态，不伪造示例数据。
5. 二级导航：修复后 `/animals`、`/groups`、`/species`、`/fund` 三组页签均横排；侧栏菜单在对应子页面正确高亮。

走查中修复的问题：

- `SectionTabs` 被全局 `nav { flex-direction: column }` 命中导致竖排，已在组件内显式覆盖。
- `ProjectDetailView`、`LedgerView` 的双栏被表格最小宽度挤压，已改为 `minmax(0, …)`；台账改为单栏，避免 7 列流水被横向截断。
- `ExpensesView` 右栏被挤到 160px，已改为 `minmax(0, …) + minmax(280px, …)`。
- 上报受理成功后详情抽屉仍显示“待受理/受理按钮”，已在成功响应后同步本地 `detail` 状态与工单号。
- 档案详情接口当前不返回 `internalNote`，编辑时曾被前端空白值误清空；已改为“原值未改动就不回传”，避免误清空。

### 配置恢复

- `vite.config.ts` 的 `/api` 代理已从走查用的 `8081` 恢复为 `http://127.0.0.1:8080`。
- `git status` 中的 `vite.config.ts` 已无改动，未把临时调试配置带入提交。

## 7. 待确认 / 已知阻塞（前端首版阶段快照）

> 下列是前端首版提交时的阻塞记录，供追溯。`internalNote`、`GET /reports/mine`、支出、逐笔台账已在 §9 闭环；`/projects` 菜单已确认学生角色具备 `fund` 菜单。

| 事项 | 现状与影响 | 建议 |
| --- | --- | --- |
| `GET /animals/{code}` 不下发 `internalNote` | 控制器固定调用 `detail(code, false)`；管理员详情页看不到内部备注，前端只能避免编辑误清空 | 后端按登录角色传管理员标记；前端已按该契约渲染，后端修好后无需再改 |
| `GET /reports/mine` 未实现 | `/report/mine` 只能做本机回执，换设备/清缓存即丢失，不是正式个人上报列表 | 后端补 `GET /reports/mine`，或在创建响应返回数据库 id 后按 id 查详情 |
| 支出接口未实现 | `/expenses` 仅有真实待复核计数，列表/筛选/登记按钮均不可用 | 等 T3.2/T3.3 定稿 `POST /expenses`、`GET /expenses`、`PUT /expenses/{no}/verify` 与状态口径 |
| 逐笔台账未实现 | `/ledger` 只有 `/stats/fund` 汇总，逐笔流水明确标注 T3.5 待接入 | 等 T3.5 定稿流水字段与分页契约后替换占位区块 |
| `/projects` 的菜单可见性 | 项目列表挂在 `fund` 菜单下；学生若没有 `fund` 菜单则无法进入，与“公开项目列表”的文档口径可能不一致 | 确认后端菜单配置；如需公开，另开公开路由或把列表设为 `public` |
| `/fund` 标题与页面标题不一致 | 侧栏仍显示后端下发的“资金透明”，页面主标题是“募捐项目” | 确认是否改后端菜单标题，或接受“资金透明”作为栏目名 |
| 校园底图缺失 | 上报页当前为 0–100 示意点位图，已标注“底图待校准” | 提供校园平面图资产与点位坐标映射后再替换底图 |
| 开发端口 / CORS | 后端默认只允许 `http://localhost:5173`；本机 5173 被占用时才临时用 5175 | 固定前端开发端口为 5173，或把 5175 加入 `critter.security.cors-allowed-origins` |
| `AuthImage` 复用范围 | 当前只用于档案封面；其他模块的鉴权附件是否复用同一 Blob 方案未定 | 先统一文件访问封装，再决定是否全站替换 |

## 8. 本地运行与验收建议

1. 后端按默认配置启动在 `8080`，前端 `npm run dev` 使用 `5173`；CORS 默认放行 5173，无需额外参数。
2. 准备至少 1 条档案、1 个 `FUNDING` 项目、1 条待受理上报，才能覆盖列表、详情与操作闭环；空库只能验证空态。
3. 管理员账号验证管理动作；另用学生账号验证上报提交、捐赠登记与“无管理入口”。
4. 重点回归：档案编辑必须填原因、捐赠登记后不得直接计入进度、上报受理后工单状态与队列同步、工单只出现后端 `allowed` 的目标状态。

## 9. 后端闭环接入（2026-11-21）

### 9.1 本轮新增/重写页面

| 页面/组件 | 变化 | 依赖接口 |
| --- | --- | --- |
| `AnimalDetailView.vue` + `AnimalStatusDialog.vue` | 管理员可发起状态迁移；时间线展示 from/to/事件/原因；按钮仅 `ANIMAL_ADMIN` 可见 | `PUT /animals/{code}/status`、`GET /animals/{code}/status-history`、`AnimalDetail.allowedStatuses` |
| `ExpensesView.vue` + 两个弹窗 | 支出列表/筛选/分页；登记表单含项目、动物、用途、金额、收款方、发生时间、票据；复核支持通过/驳回、差额提示、票据预览 | `POST/GET /expenses`、`PUT /expenses/{expenseNo}/review` |
| `LedgerView.vue` | 改为 `GET /ledger` 真实逐笔流水，支持日期/项目/方向筛选与合计 | `GET /ledger` |
| `MyReportsView.vue` | 改为服务端分页列表 + 详情抽屉；删除本机回执 | `GET /reports/mine`、`GET /reports/{id}` |
| `ProjectCreateDialog.vue` | 新建/编辑双模式，`DRAFT`/`REJECTED` 可回填并保存 | `PUT /projects/{projectNo}` |
| `SettlementCreateDialog.vue` + `SettlementView.vue` | 提交结项报告/结余去向/附件；公示页只读展示收支、报告与附件 | `PUT /projects/{projectNo}/settle`、`GET /projects/{projectNo}/settlement` |
| `GroupsView.vue` + `SightingDialog.vue` | 群体详情增加编辑、登记目击、目击时间线 | `PUT /groups/{code}`、`POST /groups/{code}/sightings`、`GET /sightings` |
| `SpeciesView.vue` + `OccurrenceCreateDialog.vue` | 物种详情增加“登记出现记录”，创建后刷新关联记录 | `POST /occurrences` |

### 9.2 口径与权限

- 金额仍全部按整数分提交与展示；支出 >50000 分显示“待二签”，复核弹窗提示“还需另一名管理员”。
- `RecordStatus` 与页面按钮不自行推断权限：状态迁移按钮限 `ANIMAL_ADMIN`，支出/结项限 `ANIMAL_ADMIN`，台账限 `ANIMAL_ADMIN`/`SYSTEM_ADMIN` 只读。
- `ExpenseReviewResult.message` 直接作为成功提示，避免前端另写一套文案。
- 结项公示页不要求管理员身份，但应用路由仍要求登录；后端匿名只读契约已通过真库验证。

### 9.3 验证边界

- 本轮只做了 `npm run build` 与真实 API 闭环验证，未重新进行浏览器点击走查；§6 的走查记录属于前端首版阶段。
- 真库验证覆盖：动物状态、支出登记/自复核拦截/两签、台账、我的上报、工单超时与再受理、结项公示、群体编辑/目击、物种出现记录。
- 前端构建：`vue-tsc -b` 无类型错误，1843 modules，BUILD 成功。
