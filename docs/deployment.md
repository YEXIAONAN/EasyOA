# 部署指南

本文档描述 EasyOA 的生产部署、升级与运维要点。

## 1. 目标架构

```
Internet → 443(HTTPS) → Nginx → easyoa-api → PostgreSQL
                            ↘ easyoa-web（静态资源）
```

- 只暴露 `80` / `443`（Nginx）与 `22`（SSH）；
- PostgreSQL **不映射宿主机端口**，仅容器内部网络可访问；
- 前端与 API 同源（同一域名），因此 Cookie / CSRF 无需任何跨域放宽配置。

## 2. 部署前准备

```bash
# 1) 拉取代码
git clone <repo-url> easyoa && cd easyoa

# 2) 配置环境变量（务必修改所有 CHANGE_ME）
cp .env.example .env
openssl rand -base64 48        # 生成 EASYOA_SESSION_SECRET
```

必填变量：

| 变量 | 说明 |
| ---- | ---- |
| `POSTGRES_PASSWORD` | 数据库密码（强随机） |
| `EASYOA_SESSION_SECRET` | 会话签名主密钥，≥ 32 字符（**泄露后必须全部重新登录**） |
| `EASYOA_BASE_URL` | 例如 `https://oa.example.com` |

## 3. TLS 证书

**生产环境**：把受信任证书放到 `infra/nginx/certs/`，命名为 `easyoa.crt` 与 `easyoa.key`
（PEM 格式；如有中间证书请合并进 `easyoa.crt`）。

**本地 / 内网试用**：使用自签名证书脚本：

```bash
./scripts/generate-self-signed-cert.sh oa.example.com
```

Let's Encrypt 用户可将 `certbot` 的 `webroot` 指向 `/var/www/certbot`
（Nginx 已预留 `/.well-known/acme-challenge/` 位置）。

## 4. 启动

```bash
docker compose up -d --build
docker compose ps
```

首次访问 `https://<域名>`：

1. 系统检测到尚未初始化 → 自动进入 `/setup`；
2. 填写组织名称、ROOT 用户名、显示名称与密码（密码策略：≥ 10 位且含字母与数字）；
3. 完成后 `/setup` 永久关闭，无法重复初始化；
4. 使用 ROOT 登录 → 工作台。

## 5. 日常运维

```bash
docker compose ps                    # 服务状态与健康检查
docker compose logs -f easyoa-api    # 后端日志（结构化，含 requestId / userId）
docker compose logs -f nginx         # 入口日志（含 request_id 与上游耗时）
docker compose restart easyoa-api    # 重启后端
docker compose down                  # 停止（数据卷保留）
docker compose down -v               # ⚠️ 停止并删除数据卷（数据库与附件将丢失）
```

数据库备份（推荐每日）：

```bash
docker compose exec postgres pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  | gzip > "easyoa-$(date +%Y%m%d).sql.gz"
```

恢复：

```bash
gunzip -c easyoa-20260101.sql.gz | docker compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"
```

## 6. 升级步骤

```bash
git pull
docker compose build easyoa-api easyoa-web
docker compose up -d
docker compose ps
```

- 数据库结构变更全部由 **Flyway 迁移脚本** 完成，后端启动时自动执行；
- 生产环境固定 `spring.jpa.hibernate.ddl-auto=validate`，不会自动改表；
- 升级前建议先做一次数据库备份。

## 7. 健康检查

| 端点 | 用途 |
| ---- | ---- |
| `https://<域名>/healthz` | 入口 Nginx 存活 |
| `https://<域名>/actuator/health` | 后端健康（DB 连通性等） |
| `docker compose ps` | 容器级 healthcheck 状态 |

`/actuator` 下的其他端点一律返回 404（不对外暴露）。

## 8. 安全清单（上线前逐项确认）

- [ ] `.env` 未提交到 Git，且 `POSTGRES_PASSWORD` / `EASYOA_SESSION_SECRET` 为强随机值
- [ ] TLS 证书为受信任证书（自签名仅限内网试用）
- [ ] 仅开放 80 / 443 / 22；数据库端口未暴露
- [ ] `/setup` 已完成初始化并关闭
- [ ] ROOT 密码强度符合策略，且未与其他人共享
- [ ] 定期备份数据库与附件卷（`easyoa-storage`）
- [ ] 关注 `security_events` 与 `audit_logs` 中的高危记录

## 9. 常见问题

| 现象 | 原因与处理 |
| ---- | ---------- |
| 浏览器提示证书不受信任 | 使用自签名证书；内网可将 CA 加入系统信任，生产请换受信任证书 |
| 登录后立刻跳回登录页 | 检查 `EASYOA_SESSION_SECRET` 是否在重启后变化（变化会使旧会话失效） |
| 后端容器不断重启 | 查看 `docker compose logs easyoa-api`；常见原因是数据库密码错误或迁移失败 |
| 上传大文件失败 | 调整 Nginx `client_max_body_size` 与 `EASYOA_STORAGE_MAX_FILE_SIZE` |