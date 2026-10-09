# EasyOA 二次开发指南

面向本地开发、功能修改和 Fork 后的二次开发。贡献回官方仓库的流程见[贡献指南](CONTRIBUTING.md)，生产安装与运维见[部署指南](deployment.md)。AI 辅助开发还需遵守 [EasyOAAgent.md](../EasyOAAgent.md)。

## 架构概览

EasyOA 使用模块化单体（Modular Monolith）：Vue 前端通过同源 `/api` 访问 Spring Boot，后端以领域模块组织业务，PostgreSQL 保存数据，磁盘或 Docker 卷保存附件。

```text
浏览器 → Vite 开发代理 / Nginx → Spring Boot → PostgreSQL
                                      └── 受控附件存储
```

- **模块化单体**：业务按领域分工，不为简单功能引入分布式基础设施或通用 CRUD 框架。
- **Frontend is never trusted**：路由守卫、隐藏按钮与前端角色判断只负责体验，后端独立授权。
- **Repository access != Authorization**：查到记录后仍须校验调用者、资源归属及数据范围，不能直接返回实体。
- **Flyway migration**：表结构只通过迁移演进，Hibernate 使用 `ddl-auto=validate`。
- **Session Authentication**：使用 Cookie 会话与 CSRF；认证信息不存入 `localStorage`。
- **EasyUI Design System**：复用设计令牌与有实际产品语义的组件，保持页面、侧栏和交互一致。

## 开发环境

| 工具 | 要求 | 用途 |
| --- | --- | --- |
| JDK | 21 | 后端运行、编译与测试 |
| Maven | 优先使用 `backend/mvnw`，Windows 为 `mvnw.cmd` | 按仓库 Wrapper 获取 Maven |
| Node.js / npm | 启动器要求 Node.js 22.12+；CI 使用 Node.js 22 | 前端开发与构建 |
| Docker / Compose | Docker 可运行，Compose v2 | 开发 PostgreSQL 与 Testcontainers |
| Bash / curl | Linux、macOS 开发入口需要 | 启动监督与健康检查 |

`frontend/package.json` 的 engines 下限为 Node.js 20.19，但统一开发入口要求 22.12+，开发时按入口要求准备。集成测试使用 `postgres:16-alpine`，首次运行需能拉取镜像。

```bash
git clone https://github.com/YEXIAONAN/EasyOA.git
cd EasyOA
```

## 一键启动

在源码根目录执行：

```bash
./easyoactl dev
```

Windows PowerShell：

```powershell
.\easyoactl.ps1 dev
```

启动器检查依赖，缺少 `.env` 时从 [.env.example](../.env.example) 生成随机数据库密码与会话密钥；已有文件保留。按锁文件安装缺失的前端依赖，启动 PostgreSQL、本机后端和前端，并等待它们就绪。开发模式标为 Development Build，不要求正式发行签名。

默认访问 <http://127.0.0.1:5173>。保持终端打开；Ctrl+C 停止本次启动的前后端，数据库、卷与 `storage/files` 附件保留。日志在 `logs/dev/session.*`，Windows 为 `session-*`。

### 环境变量如何生效

完整变量、默认值和适用范围集中在[部署指南](deployment.md)的环境变量表。

- `easyoactl dev` 按字面读取根 `.env`，不把它作为 Shell 脚本执行。开发端口、数据库身份、会话密钥及种子开关支持同名进程环境变量覆盖。
- 默认数据库 / API / Web 端口为 `5432` / `8080` / `5173`，可分别用 `POSTGRES_DEV_PORT` / `EASYOA_API_PORT` / `EASYOA_DEV_WEB_PORT` 修改。
- 入口强制 `dev` profile、回环监听和项目内的 `storage/files` 附件路径，并为 Vite 设置本次 API 代理地址。
- 已有数据库卷保留原身份与密码；修改 `.env` 不会自动修改数据库内的密码。
- 手动启动 Maven 时，Spring Boot 不会自动读取根 `.env`，需在当前终端设置应用需要的变量。不要 `source .env` 执行未知内容，也不要在命令历史中粘贴生产凭据。

macOS 启动器会尝试查找已安装的 JDK 21；仍未找到时，自行调整 `JAVA_HOME` 与 `PATH`。

### 演示数据与首次初始化

新生成的开发 `.env` 默认 `EASYOA_DEV_SEED=true`；已有文件按其设置决定。种子数据只在 `dev` profile 下运行，生产不注入演示账号。

| 账号 | 开发初始密码 | 角色 | 职位 |
| --- | --- | --- | --- |
| `root` | `EasyOA@2026` | ROOT | 系统负责人 |
| `admin` | `EasyOA@2026` | ADMIN | 运维管理员 |
| `member` | `EasyOA@2026` | MEMBER | 后端工程师 |
| `kevin` | `EasyOA@2026` | MEMBER | 前端工程师 |
| `linda` | `EasyOA@2026` | MEMBER | 产品经理 |

种子包含技术部、后端组、前端组、产品部与 Zero Lab，以及 EasyOA 演示项目，便于核对成员范围、主部门和项目角色。以上为公开的本地演示凭据，勿用于真实部署。

验证 `/setup` 时，在尚未初始化的开发数据库上设置 `EASYOA_DEV_SEED=false` 再启动。关闭种子不会撤销已有初始化状态；需要重新验证时使用独立的空开发数据库，不删除有用的数据卷。

## 分别启动与调试

### PostgreSQL

```bash
./easyoactl dev --database-only
```

Windows 使用 `.\easyoactl.ps1 dev -DatabaseOnly`。该模式不需要本机 Java / Node，只启动 [docker-compose.dev.yml](../docker-compose.dev.yml) 中的 PostgreSQL，默认仅映射 `127.0.0.1:5432`。

### Backend

在当前终端安全设置与开发数据库一致的 `POSTGRES_DB`、`POSTGRES_USER`、`POSTGRES_PASSWORD`、`EASYOA_SESSION_SECRET`，以及：

```bash
# 在仓库根目录；端口和库名按你的开发配置调整
export EASYOA_DB_URL=jdbc:postgresql://127.0.0.1:5432/easyoa
export EASYOA_STORAGE_PATH="$PWD/storage/files"
export EASYOA_DEV_SEED=false
export SERVER_ADDRESS=127.0.0.1

cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

默认 API 为 <http://127.0.0.1:8080>，Flyway 在启动时执行迁移。开发 OpenAPI 在 <http://127.0.0.1:8080/swagger-ui.html>，JSON 在 <http://127.0.0.1:8080/v3/api-docs>；`prod` profile 关闭这些文档端点。

### Frontend

另开终端，从仓库根目录执行：

```bash
cd frontend
npm ci --no-audit --no-fund
EASYOA_DEV_API_TARGET=http://127.0.0.1:8080 npm run dev -- --host 127.0.0.1
```

PowerShell 可先设置 `$env:EASYOA_DEV_API_TARGET = 'http://127.0.0.1:8080'`，再执行 `npm run dev -- --host 127.0.0.1`。后端端口变化时同步代理目标。

Vite 的 `/api` 与 `/actuator` 代理保持浏览器同源访问，保留实际 Cookie / CSRF 流程。开发 HTTP 的 Cookie `Secure=false`，生产 HTTPS 的 `Secure=true`，不能把两者描述为完全相同的安全配置。

## 项目目录

```text
EasyOA/
├── backend/
│   ├── src/main/java/com/easyoa/   # 领域模块
│   ├── src/main/resources/        # 应用配置、Flyway、构建元数据
│   └── src/test/java/             # 单元与集成测试
├── frontend/src/
│   ├── api/                      # Axios 与类型化领域接口
│   ├── components/               # EasyUI 与领域组件
│   ├── layouts/                  # Sidebar、Topbar、应用框架
│   ├── router/                   # 路由、meta、体验守卫
│   ├── stores/                   # 认证、通知及全局状态
│   ├── styles/                   # Tokens、基础样式、Element 主题
│   └── views/                    # 页面与错误状态
├── docs/                         # 人类文档、Logo、截图
├── infra/nginx/                  # 边缘代理与 TLS 目录
├── scripts/                      # 引导、发行、验证及脚本测试
├── integrity/                    # 公钥信任与发行完整性说明
├── easyoactl / easyoactl.ps1       # 开发与部署入口
├── docker-compose.yml            # 生产编排
└── docker-compose.dev.yml        # 开发数据库
```

### Backend 模块

模块通常采用 `controller / application / domain / repository / dto`；只创建有实际职责的层。Controller 负责 HTTP 与输入校验，应用服务承载用例及事务，Repository 负责持久化，API 使用 DTO，JPA 实体不直接对外暴露。

| 模块 | 职责 |
| --- | --- |
| `common` | 响应、异常、RequestId、访问日志、配置、安全过滤器 |
| `auth` / `user` | 认证、会话、密码策略、用户主数据与账号管理 |
| `system` | 初始化、系统设置、About 与构建身份 |
| `audit` / `securityevent` | 业务审计、安全事件及哈希链 |
| `organization` | 组织树、负责人、多组织归属与主部门 |
| `project` | 项目生命周期、角色、成员与权限 |
| `task` | 状态、派发、负责人、子任务、依赖与工作流 |
| `comment` / `file` | 评论历史、撤回、附件存储与资源授权 |
| `approval` | 模板版本、表单、审批人快照、流程与历史 |
| `workspace` / `notification` / `search` | 工作台聚合、业务通知、截止提醒与分组搜索 |
| `security` | TOTP、安全策略与统一高危操作通道 |
| `insights` | 按权限收敛的只读统计投影 |

`insights` 使用显式 SQL 跨领域聚合，只读，不承担跨模块业务写入。主要依赖与版本以 [pom.xml](../backend/pom.xml)、[package.json](../frontend/package.json) 和锁文件为准。

### Frontend 与 EasyUI

领域请求放在 `src/api/modules`，页面使用类型化响应。Pinia 保存真正的全局状态；普通页面请求状态留在页面或领域组件。TypeScript 保持 strict，避免用 `any` 掩盖契约问题。

优先复用 `components/easy` 的 EasyButton、EasyInput、EasySelect、EasyDrawer、EasyDialog、EasyStatus、EasyAvatar、EasyEmpty、EasyCommandPalette 与 `easyConfirm`。Element Plus 可作为底层组件使用，包装应带来一致行为或业务语义。

颜色、间距和主题复用 `styles/tokens.css`、`base.css`、`element.css`，以中性色为主、Easy Green 作重点。任务详情使用右侧 Drawer 保留看板上下文；`/projects/:id/board?task=<id>` 的刷新、关闭、前进后退都应保持 URL 与视图一致。项目当前标签是概览、看板、任务、成员、设置；时间线未交付。

页面应覆盖 loading、empty、error、forbidden、not found，表单提供校验反馈，弹层管理焦点。涉及布局时检查 1440、1280、1024 宽度及矮视口；窄屏适配不等于已交付移动端应用。

## 领域开发约定

开发前先确定状态、责任人、权限、归档行为与数据生命周期，优先复用现有服务和约束。以下是当前实现的关键边界，便于定位二次开发影响。

| 领域 | 当前约束 |
| --- | --- |
| 组织与成员 | 部门 / 团队采用 `parent_id` 树与递归查询，移动不能成环；一个用户可属于多个组织，主部门唯一，由服务与部分唯一索引共同保护。组织归档、恢复及主部门递补都有明确流程。 |
| 成员档案 | 联系方式按本人、管理员和组织负责人范围过滤；查看他人的项目与任务不会扩大当前调用者可访问的范围。ADMIN 创建普通成员，ROOT 管理管理员角色；系统保留至少一个 ROOT。 |
| 项目 | `DRAFT / ACTIVE / PAUSED / COMPLETED / ARCHIVED` 按允许的状态流转，归档后只读。OWNER 恰好一个，DEPUTY_OWNER 最多一个；转让在同一事务内维护唯一性并记录高风险审计。管理员可查看项目，不自动获得项目管理权。 |
| 任务 | 主负责人一个、副负责人最多一个、协作者多个。普通成员派发给他人进入 `PENDING_ASSIGNMENT`，由项目负责人审核；副负责人不能替换主负责人。自定义显示状态映射 `TODO / ACTIVE / REVIEW / DONE / CLOSED`，业务判断使用系统类型。 |
| 子任务、进度与依赖 | 只支持一级子任务，MANUAL 进度为 0–100，AUTO 按直接子任务计算。首次 ACTIVE / DONE 记录实际开始 / 完成时间。同项目前置依赖禁止成环；覆盖依赖需明确原因、后端授权与审计，CLOSED 为终态。 |
| 评论与附件 | 一级回复、项目成员 @、编辑版本保留；撤回保留原记录。磁盘文件使用随机存储名及分片目录，校验大小、文件名和危险扩展名；删除为软删除，下载重新校验任务 / 审批归属，不能暴露公开上传目录。 |
| 审批 | 模板新增版本，实例保留发起版本及表单 / 审批人快照。字段支持 TEXT、TEXTAREA、NUMBER、MONEY、DATE、DATETIME、SELECT、MULTI_SELECT、USER、ATTACHMENT；节点支持 ANY_ONE / ALL。 |
| 审批流转 | 动态审批人发起时解析，组织后续变化不改写实例；申请人不可自审，备用规则仍无合法人选时拒绝提交。PENDING 表单不可直接修改；退回重提从第一节点开始，拒绝 / 退回需原因，管理员转交保留历史。 |
| 工作台与通知 | 待办、审批、近期业务动态来自实际数据；动态不等于审计日志。通知只属于收件人，携带任务 / 审批 / 项目深链；截止扫描按收件人、类型、任务的未读记录去重。命令面板与顶部搜索共用 PostgreSQL 分组检索。 |
| 数据中心 | 健康度按项目超期、任务逾期计算，任务趋势统计近六周，审批效率基于已完结实例。ROOT / ADMIN 看全局，成员只看参与项目及相关审批；前端使用原生 CSS 趋势图。 |

## Flyway 与数据库

迁移放在 [backend/src/main/resources/db/migration](../backend/src/main/resources/db/migration)，使用新的 `V<编号>__<说明>.sql`，当前编号在修改前核对。主分支、发行或可能执行过的迁移不编辑、重命名或删除；更正通过新增迁移，不用 `repair` 隐藏历史变更。

结构改动需同时考虑实体映射、既有数据、索引、外键与并发。主部门、项目负责人等不变量尽量有数据库约束兜底。时间使用 UTC 语义与 `TIMESTAMPTZ`；适当使用 `jsonb`、递归查询和部分唯一索引。验证空库完整迁移与已有数据升级，数据库回退按具体恢复方案处理。

## 权限与 Audit

系统角色 `ROOT / ADMIN / MEMBER`、项目角色 `OWNER / DEPUTY_OWNER / MEMBER`、任务责任人是三个独立维度。新增用例先确定谁可以访问、资源属于谁、组织与项目范围如何计算，再在应用层复用 `ProjectPermissionService`、`TaskPermissionService` 及已有领域授权逻辑。附件授权由 `FileService` 按任务 / 审批资源执行。

Repository 查询不是授权。保护列表、搜索、档案聚合与下载范围，覆盖跨项目 ID 替换、普通成员调用管理接口、归档项目、禁用账号及撤销会话。访问不可见项目或任务时复用既有 404 语义，不泄露资源是否存在。

业务变更按需要写入 `AuditService`，包含操作者、动作、资源、必要的 before / after、原因、RequestId 与风险级别。普通路径不能编辑或删除审计；ROOT 保留期清理与其他高危操作复用 `SensitiveOperationService`，安全事件记录成功及关键验证失败。日志和审计不写密码、原始 Session、TOTP Secret 或密钥。完整安全边界与现有密钥分离差异见[安全说明](SECURITY.md)。

API 使用 DTO + Bean Validation、统一响应和机器可读错误码；不要以 Java 异常名或原始异常消息作为前端契约。401 / 403 / 404 / 409 / 422 / 429 等响应需与现有语义一致，并保留 RequestId 便于排查。

## 测试与构建

在 `backend` 执行；Windows 将 `./mvnw` 替换为 `.\mvnw.cmd`：

```bash
./mvnw test
./mvnw package
```

集成测试基类 `AbstractIntegrationTest` 使用真实 PostgreSQL、Flyway 与 `ddl-auto=validate`，测试 profile 不注入演示数据。CSRF 用基类的真实 Cookie / Header 流程。选择有意义的权限、状态、并发、约束和失败路径测试，不用 H2 或绕过授权来代替验证。

在 `frontend` 执行：

```bash
npm ci --no-audit --no-fund
npm run type-check
npm run build
# 可选：本地预览已有构建
npm run preview
```

当前前端没有 `test`、`lint` 或 `e2e` 脚本，界面行为需实际联调验证。在仓库根目录检查 Compose，使用示例配置而不覆盖本机 `.env`：

```bash
docker compose --env-file .env.example -f docker-compose.yml config -q
docker compose --env-file .env.example -f docker-compose.dev.yml config -q
```

修改命令入口或发行逻辑时，另执行 `python3 -m unittest discover -s scripts/tests -v`，记录依赖导致的跳过，并按影响补测实际服务行为。

### CI

[CI Workflow](../.github/workflows/ci.yml) 在 push、PR 和被 Release 调用时执行后端测试 / 打包、前端安装 / 类型检查 / 构建、Compose / 代理检查，以及跨平台启动脚本回归与 PowerShell 解析。CI 的 package 在 test 成功后跳过重复测试，不表示本地交付可以跳过必要测试。

[Release Workflow](../.github/workflows/release.yml) 由语义版本 Tag 触发，复用 CI 并要求签名配置；普通 PR 不发布正式包。发行进度见[发行验收记录](distribution-verification.md)，版本与发布流程见[部署指南](deployment.md)。

## 代码与 Commit 规范

沿用现有领域命名、事务边界、DTO 校验、严格类型与设计令牌。不为一次功能引入无用途的层、框架或依赖升级，不用模拟数据和占位实现宣称功能完成。提交使用 Conventional Commits、一次描述一个完整改动；具体分支、Commit 和 PR 要求见[贡献指南](CONTRIBUTING.md)。

## 常用联调与排查

| 场景 / 现象 | 操作或检查 |
| --- | --- |
| 首次初始化 | 空开发数据库，关闭种子，访问 `/setup` |
| 组织 / 项目 / 任务 | `/organization`、`/team`、`/projects`、`/projects/:id/board?task=<id>`；覆盖不同角色与刷新深链 |
| 审批 / 通知 / 搜索 | `/approvals`、顶部铃铛、⌘K / Ctrl+K；检查跳转目标与可见范围 |
| 审计 | ADMIN / ROOT 访问 `/audit-logs` 或 `GET /api/audit-logs` |
| JDK 版本错误 | 用 `java -version` 和 `backend/mvnw -version` 确认 21；检查 `JAVA_HOME` / `PATH` |
| API / Web 端口占用 | 调整对应开发端口并同步代理目标，停止自己启动的旧进程 |
| 数据库健康但登录数据库失败 | 核对既有卷身份与开发配置；不要直接删除卷 |
| Schema validation 失败 | 检查连接、Flyway 的第一处错误与迁移历史 |
| Testcontainers 启动失败 | 检查 Docker、镜像拉取与测试日志 |
| Cookie / 401 循环 | 从前端同源代理访问，使用 dev profile；检查 Cookie、CSRF 与会话撤销，不关闭安全校验 |

## 开发完成检查

- [ ] 领域状态、数据约束、归档和禁用行为明确。
- [ ] API、DTO、后端权限、数据范围与审计覆盖实际用例。
- [ ] 必要迁移新增且验证空库 / 升级，不改写执行历史。
- [ ] 前端状态、错误反馈、深链、键盘与受影响视口可用。
- [ ] 影响范围内的负向测试、类型检查、构建及配置检查完成；失败和跳过有说明。
- [ ] 功能、配置与文档一致，链接可访问，未提交秘密、运行数据或本地 Agent Skills。
- [ ] PR 说明实际完成范围、验证结果与已知限制，不把未验证项标为通过。
