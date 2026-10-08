<p align="center">
  <img src="docs/assets/logo.svg" alt="EasyOA" width="220" />
</p>

<h3 align="center">面向学生团队、小公司、工作室的现代化、轻量级、私有化部署协同办公系统</h3>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-16A34A.svg" alt="License: MIT" /></a>
  <img src="https://img.shields.io/badge/Java-21-16A34A.svg" alt="Java 21" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5-16A34A.svg" alt="Spring Boot 3.5" />
  <img src="https://img.shields.io/badge/Vue-3.5-16A34A.svg" alt="Vue 3" />
  <img src="https://img.shields.io/badge/PostgreSQL-16-16A34A.svg" alt="PostgreSQL 16" />
  <img src="https://img.shields.io/badge/version-v0.1.1-16A34A.svg" alt="v0.1.1" />
</p>

---

## 产品介绍

EasyOA 属于 Easy 系列产品，定位不是传统臃肿 OA，而是一套**可真实部署、可长期维护**的私有化协同办公系统。

核心主线：

```
项目协作 → 任务执行 → 团队沟通 → 审批流转 → 组织管理 → 安全审计
```

EasyOA 优先保证：

| 优先级 | 关注点 | 落地方式 |
| ------ | ------ | -------- |
| 1 | 项目协作体验 | 项目 / 任务 / 看板 / 侧栏详情，而不是表格堆砌 |
| 2 | 数据安全 | 后端强制鉴权、附件鉴权下载、Session 化认证 |
| 3 | 权限边界 | 系统角色 / 项目角色 / 任务角色 / 资源归属统一计算 |
| 4 | 操作可追溯 | 常规审计与安全事件只追加；ROOT 审计保留期清理须走高危操作通道并记录安全事件 |
| 5 | UI/UX 质量 | Easy 系列设计令牌 + 自有组件层，摆脱后台模板感 |
| 6 | 私有化部署体验 | `docker compose up -d` 即可自建，数据留在自己服务器 |
| 7 | 后期可维护性 | 模块化单体（Modular Monolith）+ 迁移脚本 + CI |

**重要原则：前端永远不可信。** 隐藏按钮、路由拦截、UI 条件显示都不构成权限控制；
即使用 `curl` / Postman 直接调用 API，也不得访问任何未授权数据。

---

## Screenshots

| 工作台（待办 / 待我审批 / 近期动态） | 命令面板（⌘K / Ctrl+K） |
| ------------------------------------ | ----------------------- |
| ![工作台](docs/screenshots/workspace.png) | ![命令面板](docs/screenshots/command-palette.png) |

| 登录页 | 登录页（移动端） |
| ------ | ---------------- |
| ![登录页](docs/screenshots/login.png) | ![登录页移动端](docs/screenshots/login-mobile.png) |

| 团队（成员目录 + 档案侧栏） | 组织架构（组织树 + 成员管理） |
| --------------------------- | ----------------------------- |
| ![团队](docs/screenshots/team.png) | ![组织架构](docs/screenshots/organization.png) |

| 成员档案：参与项目与近期任务（按数据范围过滤） | 近期任务可深链到看板任务侧栏 |
| ---------------------------------------------- | ---------------------------- |
| ![成员档案](docs/screenshots/member-profile-collaboration.png) | ![任务深链](docs/screenshots/member-profile-task-link.png) |

| 项目列表 | 项目详情（概览 / 看板 / 任务 / 成员 / 设置） |
| -------- | -------------------------------------------- |
| ![项目列表](docs/screenshots/projects.png) | ![项目详情](docs/screenshots/project-detail.png) |

| 任务看板（拖拽 + 阻塞标记） | 任务详情侧栏（URL 同步 `/projects/1/board?task=4`） |
| --------------------------- | -------------------------------------------------- |
| ![任务看板](docs/screenshots/task-board.png) | ![任务详情](docs/screenshots/task-drawer-details.png) |

| 忽略依赖并开始（必须填写原因） | 我的任务（跨项目 + 深链） |
| ------------------------------ | ------------------------- |
| ![依赖覆盖](docs/screenshots/task-blocked-dialog.png) | ![我的任务](docs/screenshots/my-tasks.png) |

| 任务评论（@成员 / 编辑历史 / 撤回） | 附件（受控下载 + 上传策略） |
| ------------------------------------ | ---------------------------- |
| ![评论](docs/screenshots/task-comments.png) | ![附件](docs/screenshots/task-attachment.png) |

| 审批列表（待我审批 / 我发起的） | 审批详情（流程 / 表单快照 / 历史） |
| -------------------------------- | ----------------------------------- |
| ![审批列表](docs/screenshots/approval-list.png) | ![审批详情](docs/screenshots/approval-detail.png) |

| 工作台（移动端：KPI 两列 / 内容单列） | 命令面板全局搜索（项目 / 任务 / 成员 / 审批） |
| ------------------------------------- | -------------------------------------------- |
| ![工作台移动端](docs/screenshots/workspace-mobile.png) | ![命令面板](docs/screenshots/command-palette-search.png) |

| 安全中心（动态口令 / 登录设备） | 绑定向导（二维码只展示一次） |
| ------------------------------- | ---------------------------- |
| ![安全中心](docs/screenshots/security-center.png) | ![MFA 绑定](docs/screenshots/security-mfa-enrollment.png) |

| 审计日志（风险级别 + 变更前后） | 安全事件（哈希链 + 事件数据） |
| ------------------------------- | ----------------------------- |
| ![审计日志](docs/screenshots/audit-logs.png) | ![安全事件](docs/screenshots/security-events.png) |

| 系统设置（安全策略 + ROOT 高危操作区） | 高危操作确认（影响范围 + 四要素） |
| -------------------------------------- | --------------------------------- |
| ![系统设置](docs/screenshots/security-settings.png) | ![高危操作](docs/screenshots/sensitive-operation-dialog.png) |

| 数据中心（健康度 / KPI / 完成趋势） | 逾期任务 · 成员负载 · 审批效率 |
| ----------------------------------- | ------------------------------ |
| ![数据中心](docs/screenshots/insights.png) | ![逾期与负载](docs/screenshots/insights-overdue.png) |

> 以上截图取自本地真实运行界面（v0.1.1，数据为真实数据：账号、会话、组织、项目、任务、审批与审计均已生效）。

---

## Features

### 功能一览

**账号与安全**

- **账号与认证**：Session Cookie（HttpOnly + Secure + SameSite）认证，BCrypt 哈希，密码强度策略
- **安全防护**：CSRF 双提交校验（防 BREACH 的 XOR 编码）、登录失败限制与临时锁定、Session Fixation 防护
- **会话治理**：会话记录使用 HMAC 签名存储（数据库不存原始 Session ID）、登录设备列表与撤销、改密即踢出其他设备
- **首次初始化**：`/setup` 一次性创建组织与 ROOT，完成后永久关闭（数据库原子开关保证不可重放）
- **审计基础设施**：审计日志与安全事件 Append Only（仓储层不暴露 update/delete），哈希链预留

**组织与成员**

- **组织架构**：部门 / 团队两级类型，邻接表组织树，`WITH RECURSIVE` 子树查询，**移动防成环**
- **组织生命周期**：使用归档（ARCHIVED）而非删除；存在未归档下级时禁止归档；支持恢复
- **组织负责人**：单元负责人可管理本单位（含下级）成员；同时是审批人解析（DIRECT_MANAGER / ORG_UNIT_MANAGER）的来源
- **成员多组织归属**：一个用户可属于多个部门 / 团队；**主部门唯一**（数据库 Partial Unique Index 兜底，首个归属自动成为主部门，移除主部门自动递补）
- **成员目录**：卡片式团队页面，支持关键字 / 组织（自动含下级）/ 状态筛选与分页
- **成员档案**：右侧侧栏展示组织归属、职位、联系方式（**按权限过滤**：本人 / 管理员 / 所在组织负责人可见）；并展示该成员**参与项目**（名称 / 角色 / 状态 / 进度）与**近期任务**（未结束任务的状态 / 优先级 / 截止 / 进度），均可深链到项目与任务侧栏——**查看他人档案不会扩大数据范围**，非管理员只能看到双方共有的项目与任务
- **账号管理**：创建成员（ADMIN 仅能创建 MEMBER，创建管理员需 ROOT）、启用禁用、角色变更；**禁用或改角色即刻撤销其全部会话**；系统始终保留至少一个 ROOT

**项目协作**

- **项目生命周期**：DRAFT → ACTIVE ⇄ PAUSED / COMPLETED → ARCHIVED；只允许规范内的流转（非法流转返回 409），**归档后只读且不提供删除**
- **项目角色**：OWNER × 1、DEPUTY_OWNER × 0~1、MEMBER × N——由数据库 Partial Unique Index 兜底保证唯一性，角色与系统角色彻底分离
- **权限边界**：OWNER 可设置副负责人 / 转让 / 归档；DEPUTY 可管理日常信息、状态与普通成员，但不能改 OWNER、不能归档；MEMBER 只能参与
- **OWNER 转让**：仅可转让给项目内成员，先降级原 OWNER 再提升新 OWNER（原子且唯一），并写入 CRITICAL 级审计
- **数据范围**：非项目成员访问一律返回 **404（不泄露项目是否存在）**；管理员可查看全部项目但不会自动获得项目内管理权
- **项目工作区**：概览 / 看板 / 任务 / 成员 / 设置 五个 Tab；看板与任务支持深链，Tab 与 URL 同步
- **工作台联动**：工作台仅展示「参与项目」KPI 与需要关注（已超期）的项目摘要，项目进度详情由项目管理模块承担

**任务执行**

- **任务工作流**：项目自带默认状态模板（待处理 → 进行中 → 待审核 → 已完成），支持**自定义状态**，但每个状态必须映射系统统一类型（TODO / ACTIVE / REVIEW / DONE / CLOSED），**统计与流程只依赖系统类型，不依赖自定义名称**；状态流允许向前推进与打回 / 重新打开，CLOSED 为终态
- **负责人体系**：主负责人 × 1（必填）、副负责人 × 0~1、协作成员 × N；权限边界为：主负责人 / 副负责人可改状态、进度、优先级与时间；**副负责人不能修改主负责人**；协作成员可查看、评论并完成自己负责的子任务
- **创建与派发**：OWNER / DEPUTY_OWNER 派发立即生效；普通成员指派给他人进入 **PENDING_ASSIGNMENT**，项目负责人审核通过后才正式生效（被指派成员无需再次接受）；被驳回的派发保留记录与审计但不在看板出现
- **一级子任务**：独立标题 / 负责人 / 状态 / 时间；禁止二级嵌套；支持进度 AUTO 模式（按子任务完成比例自动计算，无子任务时回退手工）
- **时间自动记录**：首次进入 ACTIVE 记录 `actual_start_at`，首次进入 DONE 记录 `completed_at`（用于后续延期 / 提前完成与周期统计）；手工模式任务首次完成时进度记为 100%
- **任务依赖**：同项目内前置依赖（B depends on A），多依赖全部完成才解除阻塞；**循环依赖在添加时被拒绝**；存在未完成依赖时无法开始，用户可「**忽略依赖并开始**」——必须填写原因、后端复查权限并写入 `TASK_OVERRIDE_DEPENDENCY` 审计
- **看板**：拖拽卡片改状态（后端校验状态流与依赖，非法流转 409 / 阻塞时弹出覆盖流程），卡片保持克制（标题 / 状态 / 优先级 / 主负责人 / 截止时间 / 进度 / Blocked 标志）
- **任务详情侧栏**：右侧 Drawer（不是大 Modal），包含状态、负责人、协作者、进度与时间、子任务、依赖与审计入口；**URL 同步**：`/projects/12/board?task=86`，刷新后仍打开对应任务
- **我的任务**：跨项目聚合我是主负责人 / 副负责人 / 协作成员的任务，支持未完成 / 即将到期 / 已完成 / 全部筛选，点击深链到看板中的任务侧栏
- **工作台联动**：「我的待办任务」「即将到期」KPI 与「我的待办」区块接入真实任务数据

**评论与文件**

- **评论**：任务评论区支持发表 / 一级回复 / @成员（ElMention，仅限项目成员）/ 附件；编辑保留**完整历史**（comment_versions 只追加），作者或项目负责人可查看历史版本
- **撤回不删除**：撤回后界面显示「某某 撤回了一条评论」，数据库保留原始评论与编辑历史，审计（`COMMENT_WITHDRAWN`）可追踪
- **附件安全**：附件不得作为公开静态资源（禁止 `/uploads/xxx.pdf`），统一下载入口 `GET /api/files/{fileId}`，链路为 **认证 → 资源权限 → 文件权限 → 下载**；非上传者下载写入 `FILE_DOWNLOADED_SENSITIVE` 审计
- **存储与元数据**：磁盘使用 UUID 随机存储名（原始文件名只做元数据），目录分片 + 路径前缀校验防目录遍历；保存 original_name / stored_name / mime_type / size / sha256 / uploader / resource_type / resource_id
- **上传策略**：大小限制、危险扩展名黑名单（exe / sh / jar 等）、空文件拒绝、文件名清洗（去目录成分与控制字符）；删除为软删除（记录与磁盘文件保留）

**审批**

- **模板化审批**：模板内容**版本化**（form_schema + node_schema 只追加新版本），新申请使用最新版本，已运行实例始终绑定发起时版本，历史审批不受模板更新影响
- **自定义审批表单**：TEXT / TEXTAREA / NUMBER / MONEY / DATE / DATETIME / SELECT / MULTI_SELECT / USER / ATTACHMENT；发起时保存 **Template Version + Form Snapshot**
- **审批实例状态机**：DRAFT → PENDING → APPROVED / REJECTED / RETURNED / CANCELLED；进入 PENDING 后申请人不可修改表单（只能撤回或等待退回）
- **多人审批**：节点支持 **ANY_ONE**（任一通过）与 **ALL**（全部通过）；当前不做 2/3、60% 投票等复杂规则
- **动态审批人**：FIXED_USER / DIRECT_MANAGER（沿组织链向上、跳过申请人）/ PRIMARY_DEPT_MANAGER / ORG_UNIT_MANAGER / PROJECT_OWNER / PROJECT_DEPUTY / SYSTEM_ROLE；**发起时解析并生成审批人快照**，组织变化不影响运行中的实例
- **自我审批禁止**：解析结果过滤申请人本人 → 命中模板覆盖的备用规则 → 系统默认递补链（通常为 ADMIN → ROOT；SYSTEM_ROLE 规则回退到 ROOT）；仍无法解析**禁止提交**（「审批流程配置不完整，请联系管理员」），绝不静默跳过节点
- **退回与重新提交**：审批人退回后申请人修改表单，**从第一个节点重新审批**（禁止从退回节点继续）；拒绝与退回必须填写原因
- **转交**：仅系统管理员可转交（原审批人 TRANSFERRED_OUT、新审批人快照标记来源），写入 CRITICAL 级审计
- **审批历史**：approval_actions 只追加，审批详情以业务语言展示流程（申请人 ✓ → 部门负责人 ●审批中 → 财务 ○等待），不暴露技术概念；审批附件走受控下载（认证 → 实例可见性 → 文件权限）

**工作台**

- **通知中心**：顶部铃铛 + 未读数（60s 轮询）；任务分配 / 状态变更 / 即将截止 / 逾期、评论 @ 与回复、审批待办 / 通过 / 拒绝 / 退回、加入项目与角色变更全部生成业务通知；**Deep Link 点击直达任务 / 审批 / 项目，不跳回首页**；通知与安全审计分离，只能查看自己的通知
- **即将截止 / 逾期**：定时扫描（可配置开关与间隔），按「收件人 + 类型 + 任务」未读去重，收件人为主 / 副负责人
- **全局搜索**：顶部搜索与命令面板共用，按 PostgreSQL 能力检索项目 / 任务 / 成员 / 审批并按类别分组（每组 5 条），数据范围与各模块一致（非成员搜不到）
- **命令面板（⌘K / Ctrl+K）**：导航 + 快捷动作（创建任务 / 创建项目 / 发起审批）+ 内容检索；「创建项目 / 发起审批」直达弹窗，「创建任务」进入项目看板自动打开新建任务
- **Activity Feed**：工作台「近期动态」为真实业务动态（谁完成任务 / 谁评论任务 / 谁发起与流转审批 / 谁调整任务状态），由业务表 + 状态变更记录实时聚合，只展示自己参与的内容，默认展示最近 5 条；明确是业务动态而非 Audit Log
- **工作台信息架构**：只保留可立即行动的内容——问候语 + 当天待处理量（真实数字）、四张 KPI（我的待办任务 / 待我审批 / 参与项目 / 即将到期）、两栏主区（我的待办、待我审批）；账号与安全信息统一在「安全中心」，不在工作台重复；项目进度不由工作台承担，仅在存在已超期项目时给出窄幅提示

**安全**

- **动态口令（TOTP）**：标准 RFC 6238（HMAC-SHA1 / 30s / 6 位，接受 ±1 时间窗），兼容 Google / Microsoft Authenticator、1Password 等标准 App；自行实现算法（不引入第三方 OTP 依赖）
- **Secret 安全**：Secret 以 **AES-256-GCM** 加密落库（密钥由 `EASYOA_SESSION_SECRET` 经 SHA-256 派生，密文带 `v1:` 版本前缀）；接口**只在绑定阶段返回一次**明文 Secret 与 `otpauth://` URI，之后任何接口都不再返回，也不写日志
- **两步绑定与双重解绑**：绑定必须先「生成 → 验证码确认」才启用（避免误绑自锁，未确认的绑定可作废）；解绑需要**当前密码 + 动态验证码**双重确认
- **登录第二步校验**：启用动态口令的账号，密码通过后仍需验证码才建立会话（验证码缺失返回 `TOTP_REQUIRED` 由前端补填，错误则计入登录失败限制）
- **ROOT 高危操作通道**：统一 `SensitiveOperationService`，所有高危动作共用同一套仪式——**重新输入当前密码 → TOTP → 填写 Reason → 展示影响范围 → 逐字输入确认短语 → 执行 → 写安全事件**；业务模块不得自行实现认证逻辑。覆盖审计日志清理、系统核心数据销毁、管理员 MFA 重置、安全策略修改、敏感数据导出
- **失败也留痕**：密码 / 验证码校验失败同样写入审计与 `PRIVILEGED_OPERATION` 安全事件（该方法刻意不带外层事务，异常不会回滚留痕记录）
- **安全策略实时生效**：登录失败阈值、锁定时长、密码最小长度、管理员是否必须绑定动态口令存储于 `system_settings`，登录保护与密码强度每次校验都读取当前值（不做「改了设置要重启」的假实现）
- **审计与安全事件视图**：审计日志按操作者 / 动作 / 资源 / 风险级别 / 时间检索，支持变更前后 JSON 展开；安全事件检索并展示 `previousHash` / `entryHash` 哈希链字段；两者均为 **Append Only**，页面不提供任何修改 / 删除入口
- **结构性保障**：安全事件仓储使用自定义只读检索片段（而非 `JpaSpecificationExecutor`，后者会暴露 `delete`），并有专门的边界测试守住「仓储层不存在 delete / update」
- **登录设备治理**：安全中心展示活跃会话（IP / 客户端 / 最近活动）与最近一次登录时间，可单独下线非当前设备

**数据中心**

- **保持克制**：只做 5 组有决策价值的数据——项目健康度、任务完成趋势、逾期任务、成员任务负载、审批效率；**不引入图表库**，趋势用原生 CSS 柱状呈现
- **项目健康度**：规则简单可解释——计划结束时间已过且未完成 → 已超期（BLOCKED）；存在逾期任务 → 有逾期（AT_RISK）；其余为正常（HEALTHY）
- **任务完成趋势**：近 6 周按周聚合「新建 vs 完成」，用于判断投入与产出是否同步
- **逾期任务 / 成员负载**：按截止时间与未完成任务数排序，点击直达任务详情与项目
- **审批效率**：基于已完结实例的「提交 → 完结」时长计算平均时长与通过率，并展示待处理 / 通过 / 拒绝 / 退回分布
- **数据范围收敛**：ROOT / ADMIN 看全局；普通成员只看自己参与的项目与「自己发起或参与审批」的记录（与其他模块一致，后端判定而非前端筛选）
- **只读报表层**：`insights` 模块是全系统唯一直接跨模块读取业务表的地方，且**只读**——报表需要 count / avg / group by 聚合，走领域服务会产生大量 N+1 与无意义内存聚合，因此用显式 SQL 直接投影为只读模型

**工程基建**

- 统一 API 响应、统一异常处理、RequestId 全链路、结构化访问日志、Flyway 迁移、OpenAPI 文档
- **设计系统**：Easy 系列 Design Tokens（90% 中性色 + 10% Easy Green）、EasyUI 组件层、Element Plus 主题映射；组件样式全部走 Token，不在页面硬编码颜色
- **界面框架**：Sidebar / Topbar / 工作台 / 登录 / 初始化 / 命令面板 / 安全中心 / 数据中心 / 审计 / 403 / 404；导航项可见性统一读取路由 meta（单一事实来源），窄屏下侧边栏自动收起为图标栏
- **登录页**：双栏布局（品牌区 + 登录表单），移动端切换单栏并保留品牌标识；页面不输出任何开发环境账号信息；保留聚焦高亮、密码显隐、Enter 提交、加载 / 错误状态与 TOTP 动态验证码输入
- **部署与 CI**：Docker Compose（Nginx + Web + API + PostgreSQL）、GitHub Actions（后端测试打包 / 前端类型检查构建 / Compose 校验 / Tag 发布）

> 规划中但**刻意不做**的能力：OAuth / SSO / LDAP、微服务拆分、Redis、多租户 SaaS、ECharts 报表堆砌。

---

## Architecture

```
Internet
   │
   ▼
[ 80 / 443 ]  Nginx（TLS 终止、安全响应头、真实 IP 覆写）
   │
   ├─────────────► easyoa-web   （Vue 3 静态资源 + SPA 回退）
   │
   └─────────────► easyoa-api   （Spring Boot 单体应用）
                        │
                        ▼
                   postgres（仅容器内部网络可达，不暴露公网）
```

后端采用 **模块化单体（Modular Monolith）**，业务模块内部按 `controller / application / domain /
repository / dto` 分层；`v0.1.x` 不引入微服务、消息队列、Redis 与 Elasticsearch
（无真实需求时不提前引入复杂度）。

| 模块 | 职责 |
| ---- | ---- |
| `common` | 统一响应、异常、RequestId、结构化日志、安全配置、工具 |
| `auth` | 登录、登出、会话、登录失败限制、密码策略 |
| `user` | 用户主数据、成员目录、成员档案、账号管理 |
| `system` | 系统设置、首次初始化 |
| `audit` | 审计日志（Append Only，查询仅 ADMIN / ROOT） |
| `securityevent` | 安全事件与哈希链 |
| `organization` | 组织树、组织归属与主部门、组织负责人 |
| `project` | 项目生命周期、项目角色与成员、项目权限 |
| `task` | 任务工作流、负责人体系、子任务、依赖与阻塞、看板与任务侧栏 |
| `comment` / `file` | 评论（回复 / @ / 编辑历史 / 撤回）与附件（受控下载 / 元数据 / 上传策略） |
| `approval` | 审批模板与版本、动态审批人、ANY_ONE / ALL、退回 / 转交 / 审批历史 |
| `notification` / `search` | 通知中心（Deep Link / 未读去重）、全局搜索（分组结果） |
| `security` | TOTP 生命周期、ROOT 高危操作通道（SensitiveOperationService）、安全策略 |
| `insights` | 数据中心只读报表层：项目健康度、任务趋势、逾期与负载、审批效率 |

---

## Tech Stack

| 层 | 技术 |
| -- | ---- |
| 后端 | Java 21 · Spring Boot 3.5 · Spring Security · Spring Data JPA · Hibernate Validator · Flyway · springdoc-openapi |
| 数据库 | PostgreSQL 16（`TIMESTAMPTZ` / `jsonb` / `WITH RECURSIVE`） |
| 前端 | Vue 3.5 · TypeScript（strict）· Vite · Vue Router · Pinia · Axios · Element Plus（仅作底层组件库） |
| 基础设施 | Docker · Docker Compose · Nginx |
| 测试 | JUnit 5 · AssertJ · MockMvc · Testcontainers（真实 PostgreSQL） |

---

## Quick Start

### 一键部署（推荐）

```bash
# 1. 准备环境变量
cp .env.example .env
#    必须修改：POSTGRES_PASSWORD、EASYOA_SESSION_SECRET（>= 32 位随机串）
#    生成随机密钥：openssl rand -base64 48

# 2. 准备 TLS 证书（本地/内网试用可自签名；生产请使用受信任证书）
./scripts/generate-self-signed-cert.sh your-domain.com

# 3. 启动
docker compose up -d --build

# 4. 查看状态
docker compose ps
```

首次访问 `https://your-domain`（自签名证书需在浏览器中信任）：

1. 进入 `/setup` 初始化：设置组织名称、ROOT 用户名与密码；
2. 初始化完成后 `/setup` 永久关闭；
3. 使用 ROOT 账号登录，即可进入工作台。

### 本地开发

见 [docs/local-development.md](docs/local-development.md)。最短路径：

```bash
cp .env.example .env
docker compose -f docker-compose.dev.yml up -d      # 仅启动 PostgreSQL（绑定 127.0.0.1）

cd backend && ./mvnw spring-boot:run                # http://localhost:8080
cd frontend && npm install && npm run dev           # http://localhost:5173
```

开发环境（`dev` profile）会自动注入演示账号：`root`（ROOT）/ `admin`（ADMIN）/ `member`（MEMBER），
初始密码均为 `EasyOA@2026`；**生产环境绝不会创建任何演示数据**。
如需验证首次初始化（`/setup`）流程，用 `EASYOA_DEV_SEED=false` 启动后端即可跳过种子数据。

---

## Docker Deployment

生产编排包含四个服务与两个隔离网络角色：

| 服务 | 说明 |
| ---- | ---- |
| `nginx` | 边缘入口：80 → 443 跳转、TLS、安全响应头、`/api` 与静态资源分流 |
| `easyoa-web` | 前端静态资源容器（Nginx，仅内部网络可达） |
| `easyoa-api` | 后端 API（JRE 21，非 root 用户运行，`/actuator/health` 健康检查） |
| `postgres` | 数据库（**不映射宿主机端口**，仅容器内部网络可访问） |

安全细节：

- 数据库端口仅 Docker 内部网络可达；
- 只有 `nginx` 暴露 `80` / `443`，`22` 保持系统 SSH；
- `X-Forwarded-For` 由 Nginx **覆写**而非追加，客户端无法伪造来源 IP；
- `/actuator/*` 仅放行 `/actuator/health`，其余返回 404。

完整部署说明与升级步骤见 [docs/deployment.md](docs/deployment.md)。

---

## Configuration

全部通过环境变量注入（`.env`，不入库、不提交 Git）：

| 变量 | 必填 | 说明 |
| ---- | ---- | ---- |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | ✅ | 数据库连接与凭据 |
| `EASYOA_SESSION_SECRET` | ✅ | 会话 HMAC 主密钥，长度 ≥ 32；当前 TOTP 加密密钥也由它派生（与开发宪法的密钥分离规则存在差异，需单独审核） |
| `EASYOA_BASE_URL` | ➖ | 对外访问地址（文档与链接生成） |
| `EASYOA_PROFILE` | ➖ | `prod`（默认） / `dev` |
| `EASYOA_SESSION_TIMEOUT_MINUTES` | ➖ | 会话有效期，默认 480 分钟 |
| `EASYOA_LOGIN_MAX_FAILURES` / `EASYOA_LOGIN_LOCK_MINUTES` | ➖ | 登录失败限制，默认 5 次 / 15 分钟 |
| `EASYOA_STORAGE_PATH` | ➖ | 附件存储目录（附件上传 / 下载使用） |
| `EASYOA_HTTP_PORT` / `EASYOA_HTTPS_PORT` | ➖ | 边缘 Nginx 端口，默认 80 / 443 |
| `EASYOA_TLS_CERT_DIR` | ➖ | 证书目录，默认 `./infra/nginx/certs` |

应用内配置（`easyoa.*`）与 Spring 配置位于 `backend/src/main/resources/application*.yml`。

---

## Security Model

> 设计冲突时的优先级：**Security > Data Integrity > Business Correctness > UX > Convenience**

### 认证与会话

- 使用 **Session Authentication**，不使用 JWT，不把 Token 放进 `localStorage`；
- Cookie 属性：`HttpOnly` + `Secure`（生产）+ `SameSite=Lax`；
- 登录成功后执行 Session Fixation 防护（更换 Session ID）；
- 会话记录表只保存 `HMAC-SHA256(主密钥, sessionId)`，数据库泄露也无法伪造会话；
- 会话被撤销（登出 / 改密 / 手动下线）后，旧 Cookie 立即失效（过滤器实时校验）。

### 动态口令与高危操作

- 支持标准 **TOTP**（RFC 6238）作为登录第二因素；Secret 以 **AES-256-GCM** 加密存储，
  只在绑定阶段返回一次明文，之后任何接口都不再返回；
- 登录时密码通过后仍需验证码才**建立会话**（未通过不产生任何登录态）；
- ROOT 高危操作（审计清理 / 数据销毁 / MFA 重置 / 安全策略修改 / 敏感数据导出）统一走
  `SensitiveOperationService`：**密码 → TOTP → Reason → 影响范围 → 逐字确认 → 执行 → 安全事件**；
- 校验失败同样留痕（该方法不带外层事务，异常不会回滚审计与安全事件）；
- 安全策略（登录阈值 / 锁定时长 / 密码长度 / 管理员强制 TOTP）存于 `system_settings`，**实时生效**。

### 授权

- 所有资源访问都必须由后端重新执行：认证 → 授权 → 数据范围 → 资源归属校验；
- 统一使用 DTO 接收输入，禁止 Controller 直接接收实体（防 Mass Assignment）；
- 错误响应统一结构，401 / 403 / 404 / 409 / 422 / 429 语义明确，且不泄露内部细节。

### 审计与安全事件

- `audit_logs` 与 `security_events` 的常规写入路径为 **Append Only**：
  仓储接口在类型层面没有 delete/update 能力，普通业务操作不提供修改或删除入口；
- ROOT 可通过 `SensitiveOperationService` 按保留期清理审计日志，需密码、TOTP、原因、
  影响范围与逐字确认，并记录安全事件；`security_events` 不提供删除路径；
- 记录字段包含 actor、action、resource、before/after、reason、IP、UA、requestId、riskLevel；
- 安全事件额外保留 `previous_hash` / `entry_hash` 哈希链字段。

### 前端不可信

前端的路由守卫、菜单隐藏、指令控制**只影响体验**。
所有权限判断在后端独立执行——绕过 Web 界面直接调用 API 同样会被拒绝。

---

## Project Structure

```
EasyOA/
├── backend/                       # Spring Boot 应用
│   ├── src/main/java/com/easyoa/
│   │   ├── common/                # 响应 / 异常 / RequestId / 日志 / 安全 / 工具
│   │   ├── auth/                  # 认证与会话
│   │   ├── user/  system/         # 用户主数据 / 系统设置与初始化
│   │   ├── audit/ securityevent/  # 审计与安全事件（Append Only）
│   │   ├── security/              # TOTP、ROOT 高危操作通道、安全策略
│   │   ├── insights/              # 数据中心（只读报表层）
│   │   ├── workspace/             # 工作台聚合
│   │   └── organization/ project/ task/ approval/ comment/ notification/ file/
│   ├── src/main/resources/db/migration/   # Flyway 迁移脚本
│   └── src/test/java/             # 单元测试 + Testcontainers 集成测试
├── frontend/                      # Vue 3 + TypeScript 应用
│   └── src/
│       ├── api/                   # Axios 客户端与类型化接口
│       ├── components/easy/       # EasyUI 设计系统组件
│       ├── layouts/               # Sidebar / Topbar / 主框架
│       ├── router/  stores/       # 路由（含守卫）与 Pinia 状态
│       ├── styles/                # Design Tokens / 基础样式 / Element 主题映射
│       └── views/                 # 登录 / 初始化 / 工作台 / 安全中心 / 数据中心 / 审计 / 403 / 404
├── infra/nginx/                   # 边缘 Nginx 配置与证书目录
├── scripts/                       # 运维脚本（自签名证书等）
├── docs/                          # 部署与开发文档、截图、Logo
├── docker-compose.yml             # 生产编排
└── docker-compose.dev.yml         # 本地开发（仅 PostgreSQL）
```

---

## Releases

| 版本 | 内容 |
| ---- | ---- |
| **v0.1.1**（当前） | 补齐**安全**（TOTP、ROOT 高危操作通道、审计与安全事件视图、安全设置）与**数据中心**（项目健康度 / 任务趋势 / 逾期与负载 / 审批效率）；修复侧边栏在矮视口下导航末项被页脚遮挡 |
| v0.1.0 | 首个版本：认证与账号、组织架构、项目协作、任务执行、评论与文件、审批、工作台，以及 Docker Compose / Nginx / CI / Flyway 工程基建 |

---

## Contributing

1. 提交信息遵循 [Conventional Commits](https://www.conventionalcommits.org/)：
   `feat(task): add dependency cycle detection` / `fix(auth): prevent unauthorized file access`
2. 每个模块必须同时覆盖：数据模型、API、权限、审计、前端、错误状态、测试；
3. 不允许只实现「Happy Path」，不允许用 TODO 假装实现核心逻辑，不允许把权限放到前端；
4. 提交前本地必须通过：

```bash
cd backend  && ./mvnw test && ./mvnw package
cd frontend && npm run type-check && npm run build
cp .env.example .env && docker compose -f docker-compose.yml config -q
```

5. 数据库结构变更必须通过 Flyway 迁移脚本；生产环境禁止 `ddl-auto=create/update`。

---

## License

[MIT](LICENSE) © 2026 EasyOA — Easy Series
