# EasyOA Distribution / Release 验收记录

验收日期：2026-10-08 至 2026-10-09。工作区版本：`0.2.0`。
本记录对应本次发行基础设施实现。业务 Phase 8 / Phase 9 已交付的范围保持不变。
以下为该次历史记录；2026-10-10 至 2026-10-11 的 RC 验收结果在文末追加。

## License / Version

- 新版本：Community Edition，`AGPL-3.0-only`，完整许可证在根 `LICENSE`。
- `NOTICE` 保留历史 MIT 版本说明及原 MIT 版权、许可文本；没有重写历史 Tag。
- 根 `VERSION`、后端 Maven、前端 package / lock 同为 `0.2.0`；Tag 必须是 `v0.2.0`。

## Created / Modified / Removed

- Created：`easyoactl`、`easyoactl.ps1`、`VERSION`、`NOTICE`、bootstrap / integrity / release 工具和回归测试、`release.yml`、公开 About API / 页面及截图、本文。
- Modified：CI、Compose / Dockerfile / Nginx、版本与构建元数据、README / 部署 / 开发文档、项目宪法、Git 忽略规则、登录 / 初始化 / 登录后源码入口。
- Removed：旧 `start.sh` / `start.ps1`，不再作为启动入口。源码没有生产签名绕过路径。
- 本地 Agent Skills 仍被 Git 忽略，未进入发行包；`AGENTS.md` / `EasyOAAgent.md` 可由 Git 跟踪。

## easyoactl Commands

`help`、`version`、`install`、`start`、`stop`、`restart`、`status`、`logs`、`doctor`、`verify`、`dev`、`backup`、`restore`、`upgrade`。

默认只显示帮助。Linux / macOS 使用 `./easyoactl`；Windows 使用 `easyoactl.ps1`。Windows 的 backup / restore / upgrade 明确拒绝执行，需使用 Linux / macOS。
生产部署使用验证通过的离线镜像；源码开发使用显式 `dev`，不伪装为官方发行。

## Official Release Verification

真实临时 Ed25519 密钥验收了清单签名、归档校验和签名，以及签名后文件哈希检查。
正式发行目录含 94 个受保护文件，最终生成六个资产：

```text
EasyOA-v0.2.0.tar.gz
EasyOA-v0.2.0.tar.gz.sha256
EasyOA-v0.2.0.tar.gz.sha256.sig
manifest.sha256
manifest.sig
release-public-key.pem
```

包内含 CLI、版本、许可证、部署说明、Compose、配置示例、签名元数据、实际 API jar、前端 dist、四个服务的离线镜像及运行脚本。扫描确认没有 `.env`、数据、附件、日志、备份、node_modules、target、Git、本地 Skills 或签名私钥。
压缩包由 Python 直接写入正式文件，避免 macOS 系统 tar 夹带 AppleDouble；签名前会验证归档结构可安全解压。

实际修改 LICENSE、API jar、清单、签名后，生产启动均在服务变化前拒绝。恢复原文件后校验成功。后端 About 的签名身份绑定编译公钥指纹、清单、VERSION 和实际运行 jar；失败时显示 Unofficial Build。
About 的身份判定覆盖 API 制品，整个部署文件集由 CLI 校验。这不能替代主机本身的安全。

验收密钥仅用于本地测试，不是 YEXIAONAN 官方发布密钥；验收结束后删除临时私钥。未配置 GitHub Secret，未把私钥写入 Git、日志、镜像构建参数或发行资产。

## GitHub Release Workflow

`.github/workflows/release.yml` 由 `push tag v*.*.*` 触发，版本 / 重复发布检查通过后复用完整 CI，然后构建 Docker、组装、签名、归档并调用 GitHub API 创建 Release、上传和核对六个资产。

采用暂存 Draft 完成上传，全部成功后才发布为非 Draft；稳定版本为普通 Release，预发布版本为 Pre-release，标题 `EasyOA vX.Y.Z`，自动生成 Release Notes。前置失败不创建 Release；上传失败只清理本次不完整 Draft；已存在的正式或 Draft Release 都拒绝覆盖。仅 publish job 的 `GITHUB_TOKEN` 具有 `contents: write`。

```text
Release Workflow Created: YES
Tag Trigger Verified: NO
GitHub Release Creation Verified: NO
Release Assets Upload Verified: NO
Signature Generation Verified: YES
Private Key Exposure: NO
```

Tag 模式、版本一致性、Workflow 语法和依赖门禁已在本地检查；GitHub 创建 / 上传协议的正常、预发布、重复和失败清理分支使用 API 测试替身验证。
真实 Tag 事件、GitHub Release 创建和资产上传：**NOT VERIFIED**。验收阶段遵循原任务禁止未经授权 push / tag / release / 设置 Secret 的约束，选择允许的本地核心逻辑验收。随后用户单独授权推送源码；普通分支 push 只触发 CI，不触发 Release。API 测试替身不算远端发布证据。

## Tests

| 验收项 | 本次结果 |
| --- | --- |
| 后端 JDK 21，完整 test + package，Testcontainers PostgreSQL | 17 份实际 Surefire 报告：113 tests，0 failures，0 errors，0 skipped；成功打包 |
| 前端 Node 22，npm ci、type-check、build | 全部通过；正式前端镜像构建通过 |
| Python CLI / release / archive 回归 | 主套件 28 项：25 通过、3 跳过；另行执行原先跳过的 2 项原生 PowerShell 测试和 1 项真实 Docker 代理测试，均通过 |
| 静态 / 配置检查 | Bash / PowerShell 解析、actionlint、Tag / 所有版本源一致、生产 / 开发 Compose、git diff --check 通过 |
| 完整发行核心 | 真实 Docker build / pull、jar 元数据一致性、94 文件清单、两种签名、归档 / SHA-256、六资产核对通过 |
| Quick installer | 使用最终真实六资产、公钥、签名和归档；验证后安全解压并调用 install 成功。下载传输及 Docker 服务调用为测试替身，GitHub 实际下载未验证 |
| 实际生产 Docker 部署 | 独立项目 / 端口：install、重复 start 保留配置、HTTPS API 健康 / 版本 / 签名身份、status / logs / doctor / restart / stop 通过 |
| 实际备份恢复 | PostgreSQL 标记行和附件经过备份、修改、restore --yes 后恢复；无确认拒绝恢复；恢复前安全备份成功 |
| 篡改拒绝启动 | LICENSE / jar / manifest / signature 篡改均真实验证拒绝；API 身份降为未验证 |
| 源码开发 | JDK 21 + Node 22 实际启动后端和 Vite；Development Build 可用；退出清理自有进程 |
| UI | 1024 / 1280 / 1440 About、错误 / 重试、首次初始化 / 登录入口、MEMBER 侧栏源码入口通过；已查看真实截图 |

主套件的跳过原因是默认运行环境未将临时 PowerShell 放入 PATH，且真实 Docker 代理测试需要显式启用；补测均完成。
原生 Windows 的真实生产服务启动：**NOT VERIFIED**。PowerShell 解析 / 密码保留 / 原生签名检查在 macOS PowerShell 7.4.13 完成，不能等同于 Windows Docker 实测。

后续 Windows CI 兼容修复：原提交的 Windows 脚本检查失败，本地以 Windows 常用 cp1252 默认编码复现了含中文 Workflow 的读取异常。测试文件、配置和子进程输出现在显式使用 UTF-8，版本同步保留 UTF-8 / LF，CI 设置 `PYTHONUTF8=1`。新增中文版本配置在非 UTF-8 默认编码下的回归用例；本地新套件 29 项，26 通过、3 跳过（当前环境无可用 PowerShell，真实代理测试需显式启用），模拟 cp1252 的 4 项版本 / Workflow 检查全部通过。真实 Windows 回归结果以本次修复提交的 GitHub CI 为准，未删除 Windows 门禁。

本地日志和测试产物位于 Git 忽略的 `.ai-local/distribution-validation/`，最终资产在 `release-validated/assets/`。测试容器、卷和进程已清理，用户原有开发数据库保持运行。

## Manual Configuration Required

1. 在仓库外生成并妥善保管官方 Ed25519 密钥，设置 Actions Secret `EASYOA_RELEASE_SIGNING_KEY`。
2. 将对应公钥提交到 `integrity/release-public-key.pem`，或设置 Repository Variable `EASYOA_RELEASE_PUBLIC_KEY`，并通过独立可信渠道公布 DER SHA-256 指纹。安装者不能仅信任同一下载源提供的公钥。
3. 发布前确认 Workflow 和本次实现已进入默认分支；获得 Tag 发布授权后推送与 VERSION 一致的 Tag。在 Actions / Releases 实际确认非 Draft Release 和全部六资产，再更新上面的三个远端验证状态。

缺少或不匹配签名配置时 Workflow 安全失败，不发布无签名包。

## Known Limitations

- 本次已实现真实发布 API 路径并完成本地完整发行核心；尚未实际创建 `v0.2.0` GitHub Release，当前不能把该版本下载链接当作已存在资产。
- 当前离线镜像归档为 linux/amd64，未实现多架构发行。
- upgrade 接收已取得的、更高稳定版本的签名目录；没有在线更新下载、自动降级或数据库自动回滚。旧未签名版本首次迁移需手动规划，不能直接使用签名目录升级路径。
- Windows backup / restore / upgrade 尚不支持；真实 Windows 生产启动待验收。
- 没有实现 Pro、License Key、激活、设备绑定或功能限制。

八项最终结论：新版本 AGPL-3.0-only **YES**；历史 MIT 说明 **YES**；取消旧 start 入口 **YES**；生产强制签名 **YES**；篡改拒绝启动实测 **YES**；源码开发可用实测 **YES**；私钥未进入 Git **YES**；未实现任何 Pro / 激活逻辑 **YES**。

## v0.2.0-rc.1 Release Candidate Validation（2026-10-10 至 2026-10-11）

本次冻结业务范围，只修复验收发现的问题并补充部署文档。主工作区四个版本源保持 `0.2.0`，README 保留待发布状态；隔离源码快照使用版本工具同步为 `0.2.0-rc.1`，真实构建、签名及部署该 RC。**签名使用一次性 Ed25519 测试密钥，验收包不是官方发行。** 私钥只位于仓库外临时目录，构建结束后删除。

### 修复

- Release / startup 测试夹具原先固定 `0.2.0`，导致合法 RC checkout 的完整脚本回归失败。改为读取 `VERSION`；稳定升级用例显式建立稳定版本夹具，增加 RC 安装与 RC Pre-release 标志回归。
- 恰好 20 MiB 的合法附件原先因 multipart 头部超过同为 20 MiB 的请求总上限而返回 413。请求总上限改为 25 MiB，与边缘 Nginx 已有 `25m` 配置一致，单文件默认上限保持 20 MiB。新增真实 Tomcat HTTP 集成测试，验证最大文件上传、下载哈希以及多 1 字节拒绝；修正部署文档中对 Nginx 默认上限的错误描述。
- 在 [部署指南](deployment.md) 增加 v0.1.1 的 **Manual Migration Required** 步骤，明确旧目录 / 卷保留、匹配数据库凭据与会话密钥、独立目标卷、恢复前安全备份及失败处理。

### 本地结果

| Gate | 结果及实际证据 |
| --- | --- |
| Backend | JDK 21，`./mvnw test` 和 `./mvnw package` 均 PASS；最终 18 份 Surefire 报告共 114 tests，0 failures / errors / skipped，真实 PostgreSQL / Flyway / JPA 校验 |
| Frontend | Node 22，`npm ci`、`npm run type-check`、`npm run build` 均 PASS |
| Compose / 静态 | 生产与开发 Compose、真实 Nginx 代理回归、Bash / PowerShell 解析、actionlint、四版本源校验、`git diff --check` PASS |
| Script regression | main 与 RC 快照完整套件各 30 项 PASS，0 skipped；包括 macOS 原生 PowerShell 7.4.13 签名与配置用例、真实 Docker 代理用例；提交前 main 完整套件再次 PASS |
| Release build / assets | 最终修复快照真实 Docker build / pull、离线镜像、94 个保护文件、六项资产、两份 Ed25519 签名、归档 SHA-256 / 安全解压 PASS；无 `.env`、Git、数据、附件、备份、日志、node_modules 或签名私钥 |
| Integrity | 正常包 PASS；文件改 1 byte、缺失文件、篡改清单、篡改签名、错误公钥、缺失清单均验证失败，生产 start 在生成配置或创建容器前拒绝 |
| Clean deployment / setup | 外部资产验证后解压至独立目录，独立数据库及附件卷，install / start PASS；新库无 Demo Seed，真实 HTTP 创建组织和 ROOT，再次初始化返回 409，浏览器 `/setup` 转向已初始化入口 |
| Business | 真实 HTTP 完成成员、组织单位、项目成员、任务状态、评论、附件、审批提交与批准、通知与已读、全局搜索、数据中心、审计；通过 Edge 验证任务抽屉 deep link、刷新、关闭、后退 / 前进和搜索跳转 |
| Permissions | ROOT / ADMIN / MEMBER 及非项目成员验证 PASS；管理操作、跨资源 ID、评论 / 附件下载 / 审批 / 通知范围、缺失 CSRF、自批、归档写入均拒绝；普通安全事件修改 / 删除无入口；禁用账号使原会话 401 |
| File size boundary | 最终签名包经 HTTPS / Nginx 上传恰好 20 MiB 并下载比对字节 / SHA-256 PASS；20 MiB + 1 byte 返回 413，已有文件仍可读取 |
| Backup / restore | 含真实业务数据和 20 MiB 附件的一致性备份 PASS；格式、校验值、私有权限与无签名私钥检查 PASS；另一新目录和全新数据卷实际 restore，登录核对 User / Organization / Project / Task / Approval / Comment / Attachment 全部 PASS，通知、审计和访问范围保留 |
| Persistence / session | 最终恢复环境实际 stop → start，再 restart，七类数据及两份附件字节 / 哈希保留；`.env` 与 TLS 哈希不变；原 servlet 会话返回 401，重新登录成功 |
| Status / doctor / logs | 真实生产容器、HTTPS、API / 数据库健康、签名身份和附件存储诊断 PASS；logs 显示真实迁移和请求记录 |
| Development mode | 独立源码、开发数据库 / 端口实际启动 API + Vite，明确 Development Build，不要求发行签名；源码改动可见，退出清理自有进程 |
| UI | Edge 实际截图检查 1440×900、1280×800、1024×768、1024×600；短视口侧栏可滚动到审计入口，任务抽屉和真实数据页面可用；viewport 已重置。最终重建包的前端 dist 哈希与已检查版本一致 |
| Legacy schema | 下载并核对 v0.1.1 官方镜像归档 SHA-256，启动旧 API 并通过 HTTP 建立业务数据；导出后恢复到最终 RC 的独立新卷，七类数据与访问范围 PASS；八项 Flyway 版本 / checksum 不变且校验成功，历史 SQL 与 v0.1.1 完全相同 |

本地证据保存在 Git 忽略的 `.ai-local/rc-validation/`，每项命令有日志、退出码和运行时间。最终资产在 `release-rc1-fixed/assets/`；最终恢复和迁移环境分别为 `easyoa-rc-fixed-restore` 与 `easyoa-rc-fixed-migrated`。测试使用合成数据，原有开发数据库卷未使用。备份含部署秘密，仅供本机私有保存，不随代码发布。

### 正式 RC 发布阻塞

通过已登录 Edge 查看仓库 Actions 配置：Repository / Environment Secrets 均为空，Repository / Environment Variables 均为空；仓库也没有 `integrity/release-public-key.pem`。

1. **Missing GitHub Secret: `EASYOA_RELEASE_SIGNING_KEY`。** 维护者需在仓库外生成并安全保管官方 Ed25519 私钥，将其配置到此 Actions Secret，不能使用本地已删除的测试密钥。
2. **缺少官方公钥与信任依据。** 提交匹配的 `integrity/release-public-key.pem`，或设置 Repository Variable `EASYOA_RELEASE_PUBLIC_KEY`，并独立公布 DER SHA-256 指纹。
3. **Official Asset Verification 未完成。** 缺少上述配置，因此未准备主分支 RC 版本、未创建 / 推送 `v0.2.0-rc.1` Tag，未触发 Release Workflow，未创建 GitHub Pre-release。正式六资产上传、重新从 GitHub 下载、验证及干净安装尚无真实证据；本地测试包和发布 API 测试替身不能替代。

### 已知限制及后续

- v0.1.1 → v0.2.0 必须手动迁移；本次已经实测，未宣称一键升级。旧 v0.1.1 Nginx 的 location 头部覆盖会发送含下划线的 upstream Host，导致旧 API 400；为了准备旧版测试数据，只在隔离旧环境显式补齐转发头。旧 Tag / 官方镜像不变，当前版本原有代理修复及其真实回归已通过。
- 当前离线镜像为 linux/amd64；Windows backup / restore / upgrade 明确不支持。macOS 的原生 PowerShell 验证不等于 Windows Docker 生产启动验收；原生 Windows 脚本结果仍由普通 push 的 GitHub CI 提供。
- MINOR：审计页文案把普通审计与永久安全事件统称为无删除入口，应说明 ROOT 受控审计清理例外；API 权限与永久安全事件边界已通过，文案延后。
- Deferred to v0.2.1：会话 / TOTP 加密密钥用途分离、Playwright 自动化、Dependabot、CodeQL、SBOM，以及上述小文案修正；本次未扩展这些范围。

**结论：NOT READY FOR v0.2.0。** 本地产品验收通过；官方签名配置、真实 RC 发布及官方下载资产验收仍是阻塞项。保持 README 待发布状态，不发布无签名包，不跳过任何门禁。
