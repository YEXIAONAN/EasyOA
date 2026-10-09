# EasyOA Distribution / Release 验收记录

验收日期：2026-10-08 至 2026-10-09。工作区版本：`0.2.0`。
本记录对应本次发行基础设施实现。业务 Phase 8 / Phase 9 已交付的范围保持不变。

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
