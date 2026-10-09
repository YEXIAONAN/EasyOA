# EasyOA 发行签名与信任

签名用于验证发布来源与受保护文件；它不限制 AGPL-3.0-only 允许的修改、运行和再分发。历史 MIT 版本保留原许可。开发使用 `easyoactl dev`，生产入口始终要求完整签名，没有跳过开关。

## 信任与文件

部署前先从独立可信渠道取得维护者公钥及其 DER SHA-256 指纹。压缩包自带公钥只方便验证，不能独立证明身份；攻击者可以替换包、公钥和签名。可信本地安装器先将下载公钥与已信任公钥比对，再验证外部 checksum 签名与 manifest 签名，验证压缩包哈希，安全解压，最后验证受保护文件。

| 文件 | 用途 |
| --- | --- |
| `release-public-key.pem` | Ed25519 公钥，允许进入 Git 与发行包 |
| `manifest.sha256` | 受保护文件的 SHA-256，发行时生成 |
| `manifest.sig` | 对 manifest 原始字节的 Ed25519 签名 |

受保护清单以 `scripts/integrity/protected-files.txt` 为统一来源，该清单自身也被签名覆盖；包括 VERSION、LICENSE、NOTICE、README、Compose、模板、CLI、运行与验证脚本、Nginx、API jar、前端 dist 全部文件及 Docker 镜像归档。缺失、修改、额外未签名 Nginx/前端文件、非法清单路径和签名错误均失败。

运行 `.env`、TLS、数据库、上传文件、日志、备份不入包或保护清单。本地 Agent Skills、Git metadata、开发缓存、私钥不得入包。

```bash
./easyoactl verify
./easyoactl start
```

Windows 使用 `.\easyoactl.ps1 verify`。校验先验证签名，再解析路径和哈希。重算哈希无法伪造签名。任何启动校验失败都返回非零，禁止加载镜像和启动生产。

完整性不能抵御已控制宿主机的人替换启动器、公钥和验证器。About 的 Official 状态由后端校验实际 jar 和编译时绑定的公钥指纹；前端不自行推断。修改后的发行版应显示 Unofficial Build，提供其对应源码。

## 维护者

在仓库外生成和保管 Ed25519 私钥，备份密钥并保护 GitHub 账号。仅将公钥提交或配置 Repository Variable。Actions 使用 Secret `EASYOA_RELEASE_SIGNING_KEY`；缺失或不匹配安全失败。公钥指纹：

```bash
openssl pkey -pubin -in /secure/release-public-key.pem -outform DER | openssl dgst -sha256
```

共享核心流程允许在本地用临时验证密钥验收：

```bash
bash scripts/release/build-release.sh --tag v0.2.0 --output /tmp/easyoa-release-check \
  --signing-key /secure/temporary.private.pem --public-key /secure/temporary-public.pem
```

这只构建、签名、归档并自检，不发布 GitHub，也不把临时密钥变成官方信任密钥。自动发布流程、资产、运维及手动配置见 [部署指南](../docs/deployment.md)。
