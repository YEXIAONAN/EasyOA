# 贡献 EasyOA

欢迎提交可复现的问题、修复、文档与改进。本文说明如何把修改贡献回官方仓库；本地安装、架构和测试命令以[二次开发指南](DEVELOPMENT.md)为主。

## 贡献前

- Bug 按[提交指南](BUG_REPORTING.md)提供环境、复现和脱敏日志；安全漏洞按[安全说明](SECURITY.md)私密报告。
- 功能或架构调整先在 [Issue](https://github.com/YEXIAONAN/EasyOA/issues) 描述使用场景、范围和取舍，避免实现后才发现与产品方向不符。
- 先检查相关代码、迁移、测试、文档和已有 PR。小范围修复或文档纠错可以直接准备 PR。
- AI 辅助开发遵守 [AGENTS.md](../AGENTS.md) 与 [EasyOAAgent.md](../EasyOAAgent.md)。本地 `.agents/skills/` 与 `.claude/skills/` 不贡献到仓库。

## Fork 与分支

Fork [官方仓库](https://github.com/YEXIAONAN/EasyOA)，在自己的 Fork 中创建分支。以下命令中的 `YOUR_USER` 替换为你的 GitHub 用户名：

```bash
git clone https://github.com/YOUR_USER/EasyOA.git
cd EasyOA
git remote add upstream https://github.com/YEXIAONAN/EasyOA.git
git fetch upstream
git switch -c waiting/fix-task-validation upstream/main
```

分支名称简短描述修改，例如 `waiting/docs-bug-reporting`。开始前检查 `git status`；不要覆盖本地未提交工作。一个 PR 聚焦一个完整问题，不混入无关重构、依赖升级或格式化。

按[开发指南](DEVELOPMENT.md)准备环境、启动和修改。涉及业务功能时，同时考虑领域状态、数据生命周期、后端权限、审计、前端状态与必要的负向测试。

## 迁移与文档

数据库变更使用新增 Flyway 迁移。进入共享分支、发行或可能已执行的迁移不能编辑、删除或重命名；验证既有数据升级和恢复方案。操作细节见开发指南，不把 Flyway repair 当作掩盖历史变更的方法。

文档按职责更新，避免同一规则多处维护：

| 内容变化 | 主要文档 |
| --- | --- |
| 产品定位、已交付能力、快速入口、阶段状态 | [README](../README.md) |
| 本地开发、架构、模块、EasyUI、测试 | [DEVELOPMENT](DEVELOPMENT.md) |
| 安装、变量、TLS、备份、恢复、升级 | [部署指南](deployment.md) |
| Bug 信息与脱敏规范 | [BUG_REPORTING](BUG_REPORTING.md) |
| 安全机制、已知边界、漏洞披露 | [SECURITY](SECURITY.md) |
| Fork、分支、提交与 PR 流程 | 本文 |

API 变化同步 DTO、前端类型与开发 OpenAPI。截图使用本地合成或脱敏数据；不能靠截图或占位界面宣称功能交付。README 保留产品信息与导航，详细规则写入对应指南。

## Testing

按改动影响执行[开发指南](DEVELOPMENT.md)中的真实检查：后端运行 test / package，前端运行 type-check / build，部署变更验证 Compose 及相关脚本 / 运行行为。纯文档修改检查相对链接、图片、Markdown 渲染和 `git diff --check`。

权限与状态变化覆盖失败路径，例如跨项目 ID、MEMBER 调用管理接口、禁用 / 撤销会话、归档资源、无权下载附件、非法审批流转。不能用绕过授权、关闭 Testcontainers 或省略失败测试代替验证。

在 PR 中记录命令与实际结果。未运行、失败、跳过或受环境限制的项目明确说明，不引用旧报告宣称本次通过。

## Commit

使用 [Conventional Commits](https://www.conventionalcommits.org/zh-hans/v1.0.0/)，一次提交描述一个连贯改动：

```text
feat(task): add dependency cycle detection
fix(file): reject cross-project attachment access
test(approval): cover self-approval fallback
docs(readme): reorganize project documentation
```

避免 `update`、`fix`、`done` 等无法说明内容的消息。提交前检查 diff，确认不包含 `.env`、私钥、数据库、日志、备份、运行附件或本地 Agent Skills。

## Pull Request

把分支推送到自己的 Fork，并向官方仓库 `main` 创建 PR。PR 标题与描述应让未读过讨论的审查者理解：

1. 具体问题或使用场景，相关 Issue。
2. 最终行为与范围，必要的前后对比。
3. 实际执行的检查、结果及未验证项。
4. 数据迁移、权限 / 审计影响、兼容性与已知限制。
5. UI 变化的脱敏截图和检查过的视口；文档变化的主要入口。

无需粘贴全部终端输出。行为改变与对应文档一起提交；CI 结果不能替代业务边界或界面运行证据。

## Code Review

审查重点是业务正确性、权限与数据范围、迁移历史、审计、并发冲突、EasyUI 一致性以及测试和文档。收到反馈后在同一分支补充修改，说明验证结果，保持 PR 聚焦最终方案。

CI 通过并完成审查后由维护者决定合并。贡献 PR 不创建官方 Tag、Release、签名密钥配置或部署变更；版本与发行由维护者按[部署指南](deployment.md)处理。贡献内容沿用仓库 [LICENSE](../LICENSE)，历史许可说明见 [NOTICE](../NOTICE)。
