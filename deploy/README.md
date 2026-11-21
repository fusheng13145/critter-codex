# 部署、备份与匿名公示入口（T4.2）

## 1. 拓扑

| 服务 | 镜像/进程 | 容器内端口 | 宿主机默认端口 | 说明 |
|---|---|---:|---:|---|
| MySQL | `mysql:8.4` | 3306 | 3307 | 库名 `critter_codex`，utf8mb4 |
| 后端 | `eclipse-temurin:21-jre` + Spring Boot jar | 8080 | 8083 | Flyway 自动建表/迁移 |
| 前端 | `nginx:1.27-alpine` + Vue 构建产物 | 80 | 8088 | `/api` 反代到后端，history 路由回退 |
| 演示数据 | 一次性 `mysql:8.4` 客户端 | — | — | `--profile demo`，导入 `qywl-demo.sql` |

## 2. Docker Compose 启动

```powershell
cd deploy
Copy-Item .env.example .env
docker compose up -d --build
# 等后端健康后，导入演示数据（会清空并重灌业务表）
docker compose --profile demo run --rm demo-data
```

访问：

- 前端：<http://127.0.0.1:8088>
- 后端健康检查：<http://127.0.0.1:8083/api/v1/projects?page=1&size=1>
- 匿名结项公示：<http://127.0.0.1:8088/projects/PRJ-2026-009/settlement>

> 本机当前未安装 Docker，Compose/Dockerfile 只做了静态编写；本地进程部署与备份恢复已实测。

## 3. 本地进程部署（当前已验证）

前置：JDK 21、Maven 3.9、MySQL 8.4、Node 22。

```powershell
# 1) 后端：连主库 critter_codex，端口 8083
cd critter-backend
$env:SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3306/critter_codex?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
$env:SERVER_PORT='8083'
mvn -B spring-boot:run

# 2) 前端：Vite 代理默认已指向 8083，可用 VITE_API_PROXY 覆盖
cd ..\critter-frontend
npm run dev
```

演示数据重置：

```powershell
cd critter-backend
.\scripts\demo\reset-demo.ps1 -Database critter_codex -Force
```

## 4. 匿名结项公示入口

- 后端：`GET /api/v1/projects/{projectNo}/settlement` 允许匿名读取，仅返回已公示结项内容。
- 前端：`/projects/:id/settlement` 的路由 `meta.public = true`，未登录守卫直接放行。
- 分享链接示例：`http://<host>/projects/PRJ-2026-009/settlement`。
- 登录用户仍可从项目详情跳转；匿名用户不会触发 `auth.restore()`，也不会被重定向到登录页。

## 5. 备份与恢复

```powershell
cd deploy

# 备份：生成 deploy/backups/critter_codex-<时间戳>.sql 与 .sha256
.\backup-mysql.ps1 -Database critter_codex

# 恢复演练：校验 SHA256 -> 建目标库 -> 导入 -> 输出行数
.\restore-mysql.ps1 -BackupFile .\backups\critter_codex-20261121-205111.sql `
  -Database critter_codex_restore_drill -Force
```

恢复脚本默认拒绝覆盖 `critter_codex`，必须显式加 `-Force`。生产建议把备份目录挂到独立磁盘或对象存储，并设置保留周期。

### 2026-11-21 演练结果

| 项目 | 结果 |
|---|---|
| 备份文件 | `critter_codex-20261121-205111.sql`，0.44 MB |
| SHA256 | `94c65f4a...d37b07`，恢复前校验通过 |
| 恢复目标 | `critter_codex_restore_drill` |
| animal / project / donation / animal_group | 200 / 12 / 1200 / 5 |
| 结论 | 备份可完整恢复，行数与主库一致 |

## 6. 生产前必改

1. `MYSQL_ROOT_PASSWORD`、`CRITTER_JWT_SECRET`、`CRITTER_AES_KEY` 必须换掉，禁止使用示例值。
2. 关闭 `critter.register.expose-verification-code`，接入真实邮件通道。
3. 前端启用 HTTPS，后端 CORS 只保留正式域名。
4. `demo-data` 会 `TRUNCATE` 业务表，禁止对生产库执行。
