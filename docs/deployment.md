# EasyOA Community Edition 部署与发行

从 v0.2.0 起，新的 Community Edition 使用 **AGPL-3.0-only**；历史 MIT 版本保持原许可证。生产统一入口为 `easyoactl`，默认显示帮助。源码开发使用 `easyoactl dev`。

## 部署前准备

生产包包含 API jar、前端 dist、四个服务镜像、Compose、运行脚本及签名。当前 GitHub Linux runner 生成 linux/amd64 镜像归档，ARM64 原生发行包尚未实现。宿主机需要 Docker、Compose v2、Bash、curl、支持 Ed25519 的 OpenSSL；安装下载、备份、恢复、升级还需要 Python 3.11+。Windows 使用原生 `easyoactl.ps1` 兼容入口；备份、恢复及升级目前在 Linux/macOS 执行。

入口 Nginx 提供 HTTPS，API、Web、PostgreSQL 仅在内部网络访问。数据库没有宿主机映射。首次启动不会创建演示账号，打开启动器显示的地址，完成 `/setup` 创建 ROOT。

## 验证下载与一键安装

先从可信源码取得安装器及其配套 scripts，再通过独立可信渠道取得维护者公钥。不能把同一次下载中的公钥直接当作身份信任依据，也不要执行未经校验的 `curl | bash`。

```bash
bash scripts/quick_start.sh --tag v0.2.0 \
  --public-key /secure/easyoa-release-public-key.pem \
  --destination /srv/EasyOA-v0.2.0
```

安装器下载六项资产，验证公钥、清单签名、压缩包校验值签名和实际 SHA-256，随后检查 tar 路径、链接、大小，再解压、验证包内文件并调用 `easyoactl install`。目标目录必须不存在。正式 v0.2.0 发布前上述下载地址尚不可用。

离线部署也必须先用独立可信公钥验证外部 `.sha256.sig`、校验压缩包，再安全解压。包内 `./easyoactl verify` 验证签名和全部受保护文件，不证明首次取得公钥的可信性。详细信任边界见 [integrity/README.md](../integrity/README.md)。

```bash
cd /srv/EasyOA-v0.2.0
./easyoactl help
./easyoactl verify
./easyoactl install
./easyoactl start
```

`install` 与 `start` 均检查依赖、签名、配置、TLS、Compose，加载已验证镜像，禁止重新构建/拉取并等待容器、HTTPS API 健康与签名身份检查。签名缺失、文件改动、占位密码、短会话密钥、生产开启开发种子数据都会失败。已有 `.env` 不覆盖；缺失时生成随机数据库密码和会话密钥，权限为 600。源码目录不能走生产入口。

## 配置与 TLS

首次正式部署在包内安全创建 `.env`，填写模板必填项并保存。已有 `.env` 直接保留。

| 变量 | 说明 |
| --- | --- |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | 数据库身份与强随机密码；不要在已有卷上随意改密码 |
| `EASYOA_SESSION_SECRET` | 至少 32 字符随机密钥；变化使原会话失效 |
| `EASYOA_BASE_URL` | 外部 HTTPS URL，例如 `https://oa.example.com` |
| `EASYOA_HTTP_PORT` / `EASYOA_HTTPS_PORT` | 默认 80 / 443 |
| `EASYOA_TLS_CERT_DIR` | 默认 `./infra/nginx/certs`；可使用外部绝对路径 |
| `EASYOA_DEV_SEED` | 生产必须为 false；开发才允许 true |
| `EASYOA_SOURCE_URL` | 可选 HTTPS 对应源码链接，供修改后的自托管构建满足源码提供义务 |

受信任 TLS 证书放在配置目录，命名 `easyoa.crt` 和 `easyoa.key`。运行时证书与 `.env` 不在发行签名清单中，允许正常配置。已有证书不会覆盖。本机试用可明确执行：

```bash
./easyoactl install --self-signed-tls
```

Windows 对应 `.\easyoactl.ps1 install -SelfSignedTls`；自签名证书不关闭发行验证。生产使用受信任证书。

## 运维命令

| 命令 | 行为 |
| --- | --- |
| `help` / `version` | 帮助 / VERSION；不启动服务 |
| `install` / `start` | 校验并幂等启动正式部署 |
| `stop` | 停止服务，保留数据卷、附件、配置、证书 |
| `restart` | 先校验，再停止并启动 |
| `status` | 版本、验证结果、服务状态、访问地址 |
| `logs [-f] [SERVICE]` | 日志；SERVICE 限定 postgres/easyoa-api/easyoa-web/nginx |
| `doctor` | 检查依赖、配置、TLS、磁盘、完整性、Compose、服务及健康端点；配置/签名失败非零退出 |
| `verify` | 仅验证发行签名及保护文件 |
| `dev [--database-only]` | 明示开发构建，启动本地开发服务 |
| `backup` | 数据库、附件、配置与 TLS 的私有一致性快照 |
| `restore DIR [--yes]` | 先验证、确认、做安全备份，再恢复并校验健康 |
| `upgrade NEW_DIR` | 验证独立的新发行目录、先备份，再复用数据卷启动新版本 |

`doctor` 对已停止服务及不可达端口显示 WARN；配置、签名、Compose 或运行服务异常显示 FAIL。它不修改部署。`/healthz` 检查入口，`/actuator/health` 检查 API/数据库。

## 备份与恢复

```bash
./easyoactl backup
./easyoactl restore /srv/EasyOA-v0.2.0/backups/easyoa-v0.2.0-TIMESTAMP-SUFFIX
```

备份短暂停止 API 写入，保存 PostgreSQL custom dump、附件 tar、VERSION、格式号、`.env` 和 TLS，生成校验清单；完成或失败均尝试恢复原先运行的 API。备份在 `backups/`，目录 700、文件 600，**包含部署秘密，应安全异地保存**；不包含发行签名私钥。

恢复拒绝缺失、损坏、危险 tar 路径、较新数据库备份和不同数据库身份/凭据。在终端输入 `RESTORE` 才执行；非交互必须明确传 `--yes`。执行前再做当前部署安全备份。失败时停止 API，保留安全备份，人工查明原因后恢复。仅校验值不能抵御攻击者重写整个本地备份，应保护备份保存渠道。

## 升级

```bash
./easyoactl upgrade /srv/EasyOA-v0.2.1
```

新包先通过可信下载流程取得，放到新的空部署目录。旧的未签名源码部署不能直接运行此 upgrade，需要先备份并人工迁移到首个签名部署目录。升级验证旧/新包、同一信任公钥以及版本严格增加，拒绝新目录已有 `.env`，先备份，再复制配置和相对目录中的 TLS，停止旧服务，从新目录启动。保持 Compose 项目名 `easyoa` 或部署时既有 `COMPOSE_PROJECT_NAME`，复用原数据库与附件卷。升级不删除旧目录，不支持降级、预发布自动升级、在线版本查询或自动下载。密钥轮换须单独审核。Flyway 升级失败时不会自动回滚数据库，须根据安全备份与迁移状态人工恢复。

## GitHub Release Pipeline

`.github/workflows/release.yml` 仅由 `push tag v*.*.*` 触发：

```text
版本/重复发布检查 → 复用完整 CI（后端测试和打包、前端检查和构建、Compose、脚本测试）
→ Docker 构建 → 正式目录 → SHA-256 manifest → Ed25519 签名
→ tar.gz → archive SHA-256 和签名 → 创建暂存 Draft → 上传核对全部资产 → 发布
```

稳定版本最终为非 Draft 的普通 Release；含语义版本预发布后缀时为非 Draft Pre-release。标题 `EasyOA vX.Y.Z`，自动生成 Release Notes。任何前置失败都阻止创建；上传失败只清理本次创建的不完整 Draft。已存在版本直接失败，不覆盖其资产。最终资产：

```text
EasyOA-vX.Y.Z.tar.gz
EasyOA-vX.Y.Z.tar.gz.sha256
EasyOA-vX.Y.Z.tar.gz.sha256.sig
manifest.sha256
manifest.sig
release-public-key.pem
```

普通 push/PR 只跑 CI。Tag 必须与根 `VERSION`、Maven、前端 package/lock 一致。修改版本后运行 `python3 scripts/release/version.py --sync`。

维护者必须先在仓库外生成 Ed25519 密钥，将私钥放到 GitHub Actions Secret `EASYOA_RELEASE_SIGNING_KEY`。公钥提交 `integrity/release-public-key.pem`，或设置 Repository Variable `EASYOA_RELEASE_PUBLIC_KEY`，并独立公布 DER SHA-256 指纹。没有匹配密钥即失败，不发布无签名包。私钥不进入仓库、Docker build 参数、包或日志。仅发布 job 使用 `contents: write` 的 `GITHUB_TOKEN`；其他 job 只读。

先把 Workflow 合入默认分支，再授权创建并推送 Tag。正式创建/上传的验证状态见 [发行验收记录](https://github.com/YEXIAONAN/EasyOA/blob/main/docs/distribution-verification.md)，本地构建成功不能替代 GitHub 运行证据。

## About 与源码

公开只读 `/api/system/about` 和 `/about` 显示 Community Edition、版本、许可证、发布者、对应源码与发布状态；初始化页、登录页和登录后侧栏都有入口。后端依据编译绑定的公钥指纹、清单签名、VERSION 和实际运行 jar 哈希提供 Official 状态；开发为 Development Build，失败为 Unofficial Build。API 只判定 API 制品，所有保护文件的校验由部署 CLI 完成。没有 Pro、激活、License Key、设备绑定或功能锁。
