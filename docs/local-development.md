# 本地开发指南

本文档面向 EasyOA 开发者，描述如何在本地运行完整开发环境。

## 1. 环境要求

| 工具 | 版本 | 说明 |
| ---- | ---- | ---- |
| JDK | 21 | 后端编译与运行 |
| Maven | 3.9+ | 或直接使用仓库自带的 `./mvnw`（推荐） |
| Node.js | 20.19+ | 前端构建（Vite 6 要求） |
| Docker + Compose | v2 | 本地 PostgreSQL 与集成测试（Testcontainers） |

> 集成测试使用 Testcontainers 启动真实 PostgreSQL，因此**本地必须能运行 Docker**。

## 2. 启动数据库

```bash
cp .env.example .env          # 首次执行；本地开发可直接使用默认值
docker compose -f docker-compose.dev.yml up -d
```

该编排只启动一个 PostgreSQL 容器，并仅绑定 `127.0.0.1:5432`（不对局域网暴露）。

## 3. 启动后端

```bash
cd backend
./mvnw spring-boot:run
```

- 默认激活 `dev` profile（`application-dev.yml`）；
- Flyway 会在启动时自动执行迁移（`src/main/resources/db/migration`）；
- 首次启动会注入演示账号：`root` / `admin` / `member`，初始密码 `EasyOA@2026`；
- 接口文档：<http://localhost:8080/swagger-ui.html>。

常用命令：

```bash
./mvnw test                  # 单元测试 + 集成测试（Testcontainers）
./mvnw -DskipTests package   # 打包
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## 4. 启动前端

```bash
cd frontend
npm install
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
| 首次初始化 | 清空数据库后访问 <http://localhost:5173/setup> |
| 重置初始化状态 | `docker compose -f docker-compose.dev.yml down -v` 后重新启动 |
| 命令面板 | `⌘K`（macOS）/ `Ctrl+K` |
| 查看审计数据 | `GET /api/audit-logs`（需 ADMIN / ROOT 登录态） |

## 6. 目录约定

```
backend/src/main/java/com/easyoa/<module>/{controller,application,domain,repository,dto}
```

- Controller 不写业务逻辑；
- Service 不直接对外暴露实体；
- 权限校验在应用层执行（`*PermissionService`，随 Phase 3 起引入）；
- 所有输入使用 DTO + Bean Validation；
- 数据库变更一律通过 Flyway 迁移脚本。

## 7. 故障排查

| 现象 | 处理 |
| ---- | ---- |
| 后端启动报 `Schema-validation: missing table` | Flyway 未执行成功，检查数据库连接与 `db/migration` 脚本 |
| 集成测试卡住 | Docker 未运行；Testcontainers 需要能拉取 `postgres:16-alpine` |
| 前端 401 循环跳转 | 检查是否通过 `npm run dev` 代理访问，而不是直连 8080 |
| Cookie 未写入 | 本地使用 `dev` profile（`secure=false`）；生产 profile 要求 HTTPS |