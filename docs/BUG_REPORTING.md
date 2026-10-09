# EasyOA Bug 提交指南

可复现的问题能更快定位。普通功能问题通过 [GitHub Issues](https://github.com/YEXIAONAN/EasyOA/issues) 提交；先搜索已有 Issue，再按本文准备信息。

**安全漏洞不要通过公开 Issue、PR、评论或公开附件报告。** 认证 / 授权绕过、IDOR、RCE、SQL 注入、敏感数据泄露、会话劫持和文件访问漏洞，请按[安全说明](SECURITY.md)私密报告。无法判断时先走私密渠道。

## 提交前

1. 查看 `/about` 或 `./easyoactl version`，记录实际运行版本和构建状态。源码开发还可用 `git rev-parse --short HEAD` 记录 Commit。
2. 阅读[部署指南](deployment.md)或[开发指南](DEVELOPMENT.md)的排查部分，核对配置、服务健康与浏览器访问方式。
3. 在自己的开发 / 测试数据上复现，记录最短步骤和结果。不要为复现删除生产数据，也不要在未备份的部署上随意升级或重置。
4. 搜索重复问题；已知 Issue 可补充新的复现信息，避免重复创建。

## 必须提供的信息

| 信息 | 示例或要求 |
| --- | --- |
| EasyOA Version | 完整版本；开发构建附 Commit，不能只写「最新版」 |
| Release Type | `Official / Development / Modified`；修改版说明改动，并附 About 显示的原始构建状态 |
| Operating System | 操作系统版本、CPU 架构；Windows 可补充 Docker Desktop / WSL 情况 |
| Deployment Method | `easyoactl / Docker / Development`；是否使用代理、自定义端口或容器配置 |
| Browser | 浏览器名称及版本；界面问题补充视口宽高、缩放比例 |
| Description | 简短标题、影响页面 / 功能、发生频率及影响范围 |
| Expected Behavior | 你预期发生什么 |
| Actual Behavior | 实际结果、错误提示或 HTTP 状态码 |
| Steps to Reproduce | 必要前置条件、角色、资源状态与逐步操作，尽量缩减为稳定复现步骤 |
| Relevant Logs | 相关时间段、时区、RequestId、脱敏后的错误；没有日志时说明原因 |
| Screenshots | 界面问题附脱敏截图或短录屏；其他问题可说明不适用 |

`Official` 不由用户自行修改配置声明；About 的身份仅描述 API 制品，完整部署校验见安全说明。`Modified` 是报告分类，用于说明修改版或未验证构建，勿据此隐瞒实际运行状态。

## 收集日志

正式部署可按受影响服务读取日志：

```bash
./easyoactl version
./easyoactl logs easyoa-api
# 界面资源或代理问题可补充
./easyoactl logs nginx
./easyoactl logs easyoa-web
```

Windows 使用 `.\easyoactl.ps1` 对应命令。开发入口日志在 `logs/dev/` 的本次会话目录，手动启动则查看各自终端。只附与问题相关的片段；浏览器问题可记录 Console 错误、请求路径、状态码、响应中的错误 code 与 RequestId。

不要直接附完整请求头、Cookie、HAR、数据库查询结果或整个日志目录。确需网络记录时先移除认证与个人数据；避免再次执行可能改变业务状态的请求。

## 脱敏检查

公开提交前删除或替换：

- 密码、数据库密码、Session ID、Cookie、认证 Token、API Key。
- TOTP 验证码、Secret、绑定二维码与 `otpauth://` 内容。
- 私钥、会话 / 加密密钥、备份密码或其他凭据。
- 敏感内部地址、域名、IP，以及姓名、联系方式、审批内容等个人或业务数据。

**不要公开上传 `.env`、数据库 dump、私钥或完整备份。** 截图需遮盖同样的信息；浏览器地址栏、二维码和展开的请求详情也要检查。用合成名称与占位内容替代真实数据。若已经误公开秘密，先撤销或轮换相关凭据，再联系维护者清理暴露内容。

## 可复制模板

将下面内容复制到 Bug Issue 中；不适用的字段保留并注明原因。

````markdown
## Environment

- EasyOA Version:
- Release Type: Official / Development / Modified
- About Build Status:
- Commit (development / modified builds):
- Operating System / Architecture:
- Deployment Method: easyoactl / Docker / Development
- Docker / Compose Version (if applicable):
- Browser / Version:
- Viewport / Zoom (UI issues):
- Local Modifications / Proxy Configuration:

## Description

受影响的页面或功能、发生频率与影响范围：

## Steps to Reproduce

前置条件（角色、项目 / 任务 / 审批状态，使用脱敏数据）：

1.
2.
3.

## Expected Behavior

预期结果：

## Actual Behavior

实际结果、错误提示或 HTTP 状态码：

## Logs

时间与时区：
RequestId（如有）：

```text
仅粘贴相关、已脱敏的日志。
```

## Screenshots

附脱敏截图，或注明不适用。

## Checks

- [ ] 已搜索相关 Issue。
- [ ] 已移除凭据、TOTP 内容、个人及敏感业务数据。
- [ ] 未上传 .env、数据库 dump、私钥或完整备份。
- [ ] 本报告不含应私密报告的安全漏洞。
````

提交后如被要求补充信息，请沿用同一 Issue。修复状态以 Issue、PR 或正式发布记录为准，不假定问题提交后会立即修复。
