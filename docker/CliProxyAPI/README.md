# CliProxyAPI Docker 部署

目录说明：

- `docker-compose.yml`：容器编排
- `config.yaml`：服务主配置
- `.env.example`：可选环境变量示例
- `auths/`：OAuth 授权与本地凭据持久化目录
- `logs/`：日志目录

当前配置适用于：

- 对外提供 API
- 使用官方管理面板
- 仅开放 `8317` 一个端口
- 宿主机持久化目录固定为 `/home/workspace/CliProxyAPI`

访问地址：

- API：`http://<服务器IP或域名>:8317`
- 管理面板：`http://<服务器IP或域名>:8317/management.html`

宿主机目录要求：

```bash
/home/workspace/CliProxyAPI/
├── config.yaml
├── auths/
└── logs/
```

建议上线前至少修改这两项：

1. `config.yaml` 里的 `remote-management.secret-key`
2. `config.yaml` 里的 `api-keys`

启动命令：

```bash
cd docker/CliProxyAPI
docker compose up -d
```

查看日志：

```bash
docker compose logs -f
```

停止服务：

```bash
docker compose down
```

说明：

- 管理面板和 API 共用 `8317` 端口。
- 当前已启用远程管理接口，建议配合服务器防火墙仅放行你的 IP。
- 如果你的服务器访问上游模型需要代理，可在 `config.yaml` 的 `proxy-url` 填写代理地址。
