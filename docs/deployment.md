# EasyOA Community Edition 部署与发行

从 v0.2.0 起，新的 Community Edition 使用 **AGPL-3.0-only**；历史 MIT 版本保持原许可证。生产统一入口为 `easyoactl`，默认显示帮助。源码开发使用 `easyoactl dev`。

**当前 v0.2.0 为待发布版本，签名包尚未正式发布。** 本文的签名安装步骤供该发行包发布后使用；当前源码的本地运行方式见源码仓库中的 `docs/DEVELOPMENT.md`。旧版部署按对应 Tag 的文档执行，不混用入口。

本文保留 `deployment.md` 路径，因为发行保护清单、打包流程和回归测试已使用该路径；它是部署配置与运维说明的唯一主文档。

## 部署前准备

生产包包含 API jar、前端 dist、四个服务镜像、Compose、运行脚本及签名。当前 GitHub Linux runner 生成 linux/amd64 镜像归档，ARM64 原生发行包尚未实现。宿主机需要 Docker、Compose v2、Bash、curl、支持 Ed25519 的 OpenSSL；安装下载、备份、恢复、升级还需要 Python 3.11+。Windows 使用原生 `easyoactl.ps1` 兼容入口；备份、恢复及升级目前在 Linux/macOS 执行。

入口 Nginx 提供 HTTPS，API、Web、PostgreSQL 仅在内部网络访问。数据库没有宿主机映射。首次启动不会创建演示账号，打开启动器显示的地址，完成 `/setup` 创建 ROOT。

## Docker 拓扑与数据

```text
Internet → Nginx :80 / :443
                 ├── easyoa-web（静态资源与 SPA 回退）
                 └── easyoa-api（Spring Boot）
                          ├── PostgreSQL
                          └── 附件卷
```

| Service | 网络与健康检查 |
| --- | --- |
| `nginx` | 唯一映射宿主机 Web 端口的边缘服务；HTTP 跳转 HTTPS，TLS 与安全响应头在此处理 |
| `easyoa-web` | 容器内静态站点，`/healthz` 检查资源服务 |
| `easyoa-api` | 容器内 API，JRE 21、非 root 用户；`/actuator/health` 检查应用及数据库 |
| `postgres` | PostgreSQL 16，只在 Docker 网络访问，`pg_isready` 检查可用性 |

四个服务使用同一个 `easyoa-internal` bridge 网络。API、Web 与 PostgreSQL 不映射宿主机端口；API 通过服务名访问数据库。Nginx 覆写客户端传入的 `X-Forwarded-For` / `X-Real-IP`，沿用请求 ID，不将不可信转发链直接交给应用。边缘 `/actuator/` 只放行健康端点，prod 关闭 OpenAPI。

`postgres-data` 保存数据库，`easyoa-storage` 保存附件；`.env`、TLS 与备份保存在宿主机。`stop` 保留这些数据，不使用 `down -v` 作为日常停止命令。仅边缘 Web 端口需公开，SSH 等宿主机管理端口由部署者自行配置。

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

已有 `.env` 保留；缺失时 `install` 从 `.env.example` 生成数据库密码与会话密钥。在开放访问前核对域名、端口和证书目录。如手动从模板创建，只在文件不存在时操作，并替换全部 `CHANGE_ME`，不要覆盖原部署配置。

### 环境变量

下表集中维护开发与生产的变量。Compose 的 `.env` 替换与应用读取环境变量是两步；应用支持某个变量不代表当前 Compose 会把它传入容器。

| 变量 | 默认 / 要求 | 生效范围与说明 |
| --- | --- | --- |
| `POSTGRES_DB` / `POSTGRES_USER` | 模板均为 `easyoa`；生产必填 | Compose 数据库与 API 身份；已存在卷不会重新初始化身份 |
| `POSTGRES_PASSWORD` | 生产必填，启动器要求至少 16 字符且非占位 / 已知弱值 | 敏感；首次自动生成随机值，不在已有卷上直接改密码 |
| `EASYOA_SESSION_SECRET` | 生产必填，至少 32 字符随机值 | 敏感；会话 HMAC，当前还派生 TOTP 加密密钥，轮换需恢复方案 |
| `EASYOA_BASE_URL` | 模板为 `https://localhost` | 外部 HTTPS 访问地址，启动器显示；正式上线设置实际域名 |
| `EASYOA_PROFILE` | Compose 默认 `prod` | 生产入口要求 prod / 未设置，并强制 prod；`dev` 入口强制 dev |
| `EASYOA_SESSION_TIMEOUT_MINUTES` | `480` 分钟 | 会话超时，当前生产 Compose 传入 API |
| `EASYOA_LOGIN_MAX_FAILURES` / `EASYOA_LOGIN_LOCK_MINUTES` | `5` 次 / `15` 分钟 | 后端回退值；数据库安全设置存在时优先使用系统设置 |
| `EASYOA_STORAGE_PATH` | 生产 `/var/lib/easyoa/storage` | Compose 附件卷挂载与 API 路径；dev 入口固定为项目内 `storage/files` |
| `EASYOA_STORAGE_MAX_FILE_SIZE` | `20971520` 字节（20 MiB） | 应用 multipart 上限；当前生产 Compose 未转发，单改 `.env` 不生效 |
| `EASYOA_ORG_NAME` | `Easy Studio` | 应用初始化页面默认组织名；当前生产 Compose 未转发，初始化时也可填写 |
| `EASYOA_HTTP_PORT` / `EASYOA_HTTPS_PORT` | `80` / `443` | 边缘 Nginx 的宿主机端口 |
| `EASYOA_TLS_CERT_DIR` | `./infra/nginx/certs` | 宿主机证书目录，可为绝对路径 |
| `EASYOA_SOURCE_URL` | 留空 | 可选 HTTPS 对应源码链接；留空使用构建对应的官方源码链接，修改版应配置实际源码 |
| `EASYOA_DEV_SEED` | 模板 / 生产为 `false`，新生成开发配置为 `true` | 只在 dev profile 注入种子；生产入口拒绝 true / 1 |
| `POSTGRES_DEV_PORT` | `5432` | 开发数据库仅映射回环地址 |
| `EASYOA_API_PORT` / `EASYOA_DEV_WEB_PORT` | `8080` / `5173` | 本机开发端口；当前生产 Compose 不转发 API 端口，容器固定 8080 |
| `EASYOA_DB_URL` | dev 默认为 `jdbc:postgresql://localhost:5432/easyoa` | 手动开发启动的 JDBC 地址；dev 入口按数据库端口与库名生成；prod 使用容器数据库地址 |
| `EASYOA_DEV_API_TARGET` | Vite 默认 `http://localhost:8080` | 手动前端的开发代理目标；dev 入口自动设置 |
| `POSTGRES_HOST` / `POSTGRES_PORT` | prod 为 `postgres` / `5432` | Compose 容器内部数据库地址，不是外部访问入口 |
| `COMPOSE_PROJECT_NAME` | 编排名称为 `easyoa`，开发为 `easyoa-dev` | 已有部署保持原项目名，升级须复用同一组卷 |

应用绑定以 `backend/src/main/resources/application*.yml` 与配置类为准。dev 入口只主动导出其使用的开发变量，其他应用参数在手动启动的进程环境中显式设置。生产 Compose 未转发的参数需要受控的部署变更；修改发行包中的 Compose / Nginx 会使签名校验失败，不能靠编辑受保护文件后跳过验证上线。

**密钥轮换限制：** 当前 TOTP 密钥仍由会话密钥派生，与用途分离的开发规则存在差异。更换 `EASYOA_SESSION_SECRET` 会影响原会话和已绑定 TOTP 解密；先准备数据库 / 配置备份、迁移与账号恢复方案，不能只按“重新登录”处理。

受信任 TLS 证书放在配置目录，命名 `easyoa.crt` 和 `easyoa.key`。运行时证书与 `.env` 不在发行签名清单中，允许正常配置。已有证书不会覆盖。本机试用可明确执行：

```bash
./easyoactl install --self-signed-tls
```

Windows 对应 `.\easyoactl.ps1 install -SelfSignedTls`；自签名证书不关闭发行验证。生产使用受信任证书。

证书为 PEM 格式，中间证书合并到 `easyoa.crt`。Nginx 预留 ACME challenge 路径，但当前编排未提供 certbot 的 webroot 挂载或自动续期流程；不要把预留位置当作已完成证书自动化。

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

先把 Workflow 合入默认分支，再授权创建并推送 Tag。源码仓库的 `docs/distribution-verification.md` 记录正式创建 / 上传的验证状态；发行包不包含此研发记录。本地构建成功不能替代 GitHub 运行证据。

## About 与源码

公开只读 `/api/system/about` 和 `/about` 显示 Community Edition、版本、许可证、发布者、对应源码与发布状态；初始化页、登录页和登录后侧栏都有入口。后端依据编译绑定的公钥指纹、清单签名、VERSION 和实际运行 jar 哈希提供 Official 状态；开发为 Development Build，失败为 Unofficial Build。API 只判定 API 制品，所有保护文件的校验由部署 CLI 完成。没有 Pro、激活、License Key、设备绑定或功能锁。

## 排查与上线检查

```bash
./easyoactl status
./easyoactl doctor
./easyoactl logs easyoa-api
./easyoactl logs nginx
```

先从 `doctor` 的首个 FAIL、服务日志和 RequestId 排查。分享日志前脱敏，不上传 `.env`、数据库 dump、私钥或完整备份；安全漏洞通过维护者私密渠道报告，具体方式见源码仓库的 `docs/SECURITY.md`。

| 现象 | 检查与处理 |
| --- | --- |
| 源码目录 `install/start` 被拒绝 | 正式入口需要签名发行包；源码运行使用 `dev`，不跳过验证 |
| 签名或文件哈希失败 | 核对可信公钥、包版本及下载完整性；保留配置和数据，重新取得可信包，不重算清单伪装官方发行 |
| 数据库 / 会话配置被拒绝 | 替换模板占位值，核对密码长度与随机性；已有卷按原身份处理 |
| 证书缺失 / 不受信任 | 配置 `easyoa.crt`、`easyoa.key` 与完整证书链；生产使用受信任证书，本机试用显式自签名 |
| 容器反复重启 | 查看 API / 数据库日志的第一处错误，检查数据库密码、Flyway 和磁盘；先备份，不删除卷 |
| HTTPS 健康检查失败 | 检查端口冲突、Nginx、证书挂载、API 健康和 About 签名身份 |
| 重启后登录失效 / TOTP 验证失败 | 检查是否更换会话密钥；按密钥轮换和恢复方案处理，不反复替换随机密钥 |
| 上传失败 / 413 | 应用 multipart 默认 20 MiB；边缘 Nginx 未显式设置 `client_max_body_size`，还受代理默认限制。单改 `.env` 中文件上限不会传入当前生产容器，部署调整需同时处理各层和签名 |
| 恢复或升级失败 | API 保持停止，保留安全备份；核对备份格式、数据库身份和迁移状态后人工恢复，不假定自动回退 |
| ARM64 / Windows 运维受限 | 当前归档为 linux/amd64；Windows backup / restore / upgrade 尚不支持，按已验证平台执行 |

开放部署前确认：

- 域名、HTTPS、证书与宿主机访问控制符合实际环境。
- `.env`、TLS、附件、数据库卷和备份的权限及安全存放位置明确。
- ROOT 使用独立强密码，完成 TOTP 绑定后再启用管理员强制策略。
- 数据库与附件一起备份，保留恢复所需的配置及密钥，并在隔离环境演练恢复。
- `verify`、服务健康与必要业务操作核对完成；持续关注高风险审计与安全事件。
