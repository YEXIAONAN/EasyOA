# 发行完整性工具

`package-release.sh` 从已构建镜像提取 API jar 与 Web dist，按明确列表复制部署文件，导出四个 Docker 服务镜像，生成 manifest、签名并自检。公私钥必须匹配，输出目录必须不存在。

`protected-files.txt` 为生成器和 Bash/PowerShell 验证器共享的必要文件清单；其自身被清单保护。全部前端文件和 Nginx 配置动态补充，避免新增文件逃逸。生成器只计算哈希，不具有签名能力。

生产由 `easyoactl` / `easyoactl.ps1` 调用验证器，先验签、再解析清单并检查 SHA-256；失败立即停止。开发入口为 `easyoactl dev`，明确标识开发构建。

```bash
python3 -m unittest discover -s scripts/tests -v
```

测试使用临时目录、真实临时 Ed25519 签名及工具替身；不会访问开发者 `.env` 或数据。完整共享构建流程在 `scripts/release/build-release.sh`，发布 API 在 `scripts/release/publish.py`。详见 [部署指南](../../docs/deployment.md) 和 [信任说明](../../integrity/README.md)。
