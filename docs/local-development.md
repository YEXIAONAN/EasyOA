# 本地开发指南

本文档面向 EasyOA 开发者，描述如何在本地运行完整开发环境。

## 1. 环境要求

| 工具 | 版本 | 说明 |
| ---- | ---- | ---- |
| JDK | 21 | 后端编译与运行 |
| Maven | 3.9+ | 或直接使用仓库自带的 `./mvnw`（推荐） |
| Node.js | 22.12+ | 前端构建（现有 VueUse 依赖与构建要求） |
| Docker + Compose | v2 | 本地 PostgreSQL 与集成测试（Testcontainers） |

> 集成测试使用 Testcontainers 启动真实 PostgreSQL，因此**本地必须能运行 Docker**。

## 2. 一键启动（推荐）

在源码根目录执行：

```bash
./easyoactl dev
```

Windows PowerShell：

```powershell
.\easyoactl.ps1 dev
```

启动器检查 JDK 21、Node.js 22.12+、Docker 与 Compose v2；缺少 `.env` 时生成随机数据库密码与会话密钥，
已有文件保持原样。按锁文件安装缺失的前端依赖，然后依次等待 PostgreSQL、后端和前端就绪。
数据库及本机服务仅监听回环地址，开发前端默认访问 `http://127.0.0.1:5173`。

窗口保持运行，Ctrl+C 会停止本次启动的前后端进程，保留 PostgreSQL、数据卷与 `storage/files` 附件。
日志在 `logs/dev/session.*`（Windows 为 `session-*`）；后端或前端提前退出时，启动器报错并清理本次应用进程。
开发模式强制使用 `dev` profile，前端 `/api` 与 `/actuator` 代理会自动指向本次后端。

| `.env` 配置 | 默认值 | 用途 |
| ---- | ---- | ---- |
| `POSTGRES_DEV_PORT` | `5432` | 本机 PostgreSQL 端口 |
| `EASYOA_API_PORT` | `8080` | 本机 API 端口 |
| `EASYOA_DEV_WEB_PORT` | `5173` | 本机前端端口 |
| `EASYOA_DEV_SEED` | 新建开发配置为 `true` | 演示数据开关；设为 `false` 验证首次初始化 |

开发入口允许同名环境变量覆盖这些配置。`.env` 只按字面读取，不作为脚本执行。
本机附件路径固定为项目下的 `storage/files`，避免误用模板里的生产容器路径。
macOS 会在当前 Java 不是 21 时尝试查找已安装的 JDK 21；否则需要自行设置 `JAVA_HOME` 与 `PATH`。
首次 Maven 运行可能下载依赖；已有数据库卷的账号密码必须与 `.env` 一致，修改文件不会自动改数据库密码。

## 3. 分别启动（需要独立调试时）

先启动数据库：

```bash
./easyoactl dev --database-only
```

Windows 使用 `.\easyoactl.ps1 dev -DatabaseOnly`，此模式无需本机 Java / Node。
该编排只启动一个 PostgreSQL 容器，并仅绑定 `127.0.0.1:5432`（不对局域网暴露）。

### 启动后端

手动启动时，必须在当前终端设置与 `.env` 一致的 `POSTGRES_DB`、`POSTGRES_USER`、`POSTGRES_PASSWORD`、
`EASYOA_SESSION_SECRET` 和 `EASYOA_DB_URL=jdbc:postgresql://127.0.0.1:<数据库端口>/<数据库名>`；后端不会自动加载根目录 `.env`。
设置 `EASYOA_STORAGE_PATH` 为本机附件目录，并按需要设置 `EASYOA_DEV_SEED`。

```bash
cd backend
./mvnw spring-boot:run
```

- 默认激活 `dev` profile（`application-dev.yml`）；
- Flyway 会在启动时自动执行迁移（`src/main/resources/db/migration`）；
- 首次启动会注入演示数据（见下表）；无需演示数据时设置 `EASYOA_DEV_SEED=false`；
- 接口文档：<http://localhost:8080/swagger-ui.html>。

### 演示数据（仅 dev profile）

| 账号 | 密码 | 角色 | 职位 |
| ---- | ---- | ---- | ---- |
| `root` | `EasyOA@2026` | ROOT | 系统负责人 |
| `admin` | `EasyOA@2026` | ADMIN | 运维管理员 |
| `member` | `EasyOA@2026` | MEMBER | 后端工程师 |
| `kevin` | `EasyOA@2026` | MEMBER | 前端工程师 |
| `linda` | `EasyOA@2026` | MEMBER | 产品经理 |

组织树：`技术部`（负责人 admin）→ `后端组`（member）/ `前端组`（kevin）；`产品部`（linda）；`Zero Lab`（root）。
组织归属按「主部门 + 兼任」安排。
演示项目：`EasyOA`（进行中，35%，OWNER = root，副负责人 = admin，成员含 member / kevin / linda），
可直接用于验证项目列表、项目详情、角色边界与工作台联动。

常用命令：

```bash
./mvnw test                  # 单元测试 + 集成测试（Testcontainers）
./mvnw -DskipTests package   # 打包
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### 启动前端

```bash
cd frontend
npm ci
npm run dev                  # http://localhost:5173
```

Vite dev server 会把 `/api` 与 `/actuator` 代理到 `http://localhost:8080`
（见 `vite.config.ts`）。浏览器始终与前端同源，因此 **Cookie 与 CSRF 行为与生产环境完全一致**，
不存在为了本地调试而放宽安全策略的配置。

如果需要指向其他后端地址：

```bash
EASYOA_DEV_API_TARGET=http://192.168.1.10:8080 npm run dev
```

前端脚本：

```bash
npm run type-check           # TypeScript strict 检查（CI 必过）
npm run build                # 类型检查 + 生产构建
npm run preview              # 预览构建产物
```

## 5. 常用联调路径

| 场景 | 操作 |
| ---- | ---- |
| 首次初始化 | 清空数据库后访问 <http://localhost:5173/setup>（或 `EASYOA_DEV_SEED=false` 启动后端） |
| 重置环境（含演示数据） | `docker compose -f docker-compose.dev.yml down -v` 后重新启动后端 |
| 命令面板 | `⌘K`（macOS）/ `Ctrl+K` |
| 查看审计数据 | `GET /api/audit-logs`（需 ADMIN / ROOT 登录态） |
| 组织与成员 | `/organization`、`/team`；接口 `GET /api/org-units`、`GET /api/users/directory` |
| 项目协作 | `/projects`、`/projects/:id`；接口 `GET /api/projects`、`GET /api/projects/{id}`、`POST /api/projects/{id}/transfer-owner` |

## 6. 目录约定

```
backend/src/main/java/com/easyoa/<module>/{controller,application,domain,repository,dto}
```

- Controller 不写业务逻辑；
- Service 不直接对外暴露实体；
- 权限校验在应用层执行（统一使用 `*PermissionService`，禁止在业务代码里散落角色判断）；
- 所有输入使用 DTO + Bean Validation；
- 数据库变更一律通过 Flyway 迁移脚本。

## 7. 故障排查

| 现象 | 处理 |
| ---- | ---- |
| 一键启动提示 JDK 版本错误 | 安装 JDK 21，检查 `JAVA_HOME` 与 `PATH`；JDK 17 无法编译本项目 |
| API / Web 端口被占用 | 修改 `.env` 中对应开发端口；停止旧开发进程后重试 |
| PostgreSQL 健康但 API 认证失败 | 既有数据库卷的密码与 `.env` 不一致；核对原配置，不要直接删除数据卷 |
| 后端启动报 `Schema-validation: missing table` | Flyway 未执行成功，检查数据库连接与 `db/migration` 脚本 |
| 集成测试卡住 | Docker 未运行；Testcontainers 需要能拉取 `postgres:16-alpine` |
| 前端 401 循环跳转 | 检查是否通过 `npm run dev` 代理访问，而不是直连 8080 |
| Cookie 未写入 | 本地使用 `dev` profile（`secure=false`）；生产 profile 要求 HTTPS |
