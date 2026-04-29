---
description: 连接 Niro 远程服务器并查看测试环境状态
argument-hint: "[extra-remote-command]"
---

# Connect Niro Server

连接 Niro 远程服务器 `root@106.53.11.158`，默认使用本机私钥 `~/.ssh/niro_server_ed25519`。这个命令的主要功能是建立连接、确认远程主机状态、查看当前测试环境容器与监听端口；默认不拉取项目、不构建镜像、不启动或重启容器。

## What This Command Does

1. **检查 SSH 私钥**：确认 `~/.ssh/niro_server_ed25519` 存在，不读取私钥内容。
2. **检查远程连通性**：通过 SSH 执行 `hostname && whoami && pwd`。
3. **查看测试环境状态**：输出 `niro-*`、`xxl-job-*`、`rmq*`、Redis、数据库等相关容器状态与端口。
4. **查看监听端口**：输出远程服务器当前 TCP 监听端口，便于判断本地连接测试环境需要开放哪些端口。
5. **执行可选远程命令**：如果 `$ARGUMENTS` 非空，则在连接检查后执行用户传入的额外远程命令。

## Usage

```bash
/connect-niro-server
/connect-niro-server "docker logs --tail 100 niro-web-test"
/connect-niro-server "cd /home/app/niro && git status --short"
```

## Implementation Steps

When this command is invoked:

### 1. Prepare connection settings

Use these fixed settings unless the user explicitly asks to change them:

```bash
SSH_KEY="$HOME/.ssh/niro_server_ed25519"
SSH_TARGET="root@106.53.11.158"
SSH_OPTS="-i $SSH_KEY -o IdentitiesOnly=yes -o BatchMode=yes -o ConnectTimeout=10"
REMOTE_PROJECT_DIR="/home/app/niro"
```

Do not print private key content. Do not ask the user for passwords.

### 2. Check private key file

Use Bash tool to run:

```bash
test -f "$HOME/.ssh/niro_server_ed25519"
```

If the file does not exist:

- Stop immediately.
- Report that the SSH private key is missing.
- Tell the user to generate or place the key at `~/.ssh/niro_server_ed25519`.

### 3. Run connection check

Use Bash tool to run:

```bash
ssh -i "$HOME/.ssh/niro_server_ed25519" \
  -o IdentitiesOnly=yes \
  -o BatchMode=yes \
  -o ConnectTimeout=10 \
  root@106.53.11.158 'hostname && whoami && pwd'
```

Expected successful output includes:

```text
VM-0-15-ubuntu
root
/root
```

If SSH exits non-zero:

- Report the exact SSH error.
- Do not retry with password.
- Suggest checking key binding, server security group, SSH service, and `authorized_keys`.

### 4. Inspect current test environment status

Use Bash tool to run this read-only remote status command:

```bash
ssh -i "$HOME/.ssh/niro_server_ed25519" \
  -o IdentitiesOnly=yes \
  -o BatchMode=yes \
  -o ConnectTimeout=10 \
  root@106.53.11.158 \
  'hostname && whoami && docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" && printf "\nLISTEN PORTS\n" && ss -lntp'
```

Expected successful outcome:

- SSH returns exit code 0.
- The output shows current container status and exposed ports.
- No project files are changed.
- No containers are built, started, stopped, restarted, or recreated.

### 5. Execute optional extra remote command

If `$ARGUMENTS` is empty:

- Stop after connection check and status output.
- Report that the server connection was successful and current test environment status was displayed.

If `$ARGUMENTS` is not empty:

- Treat `$ARGUMENTS` as an extra remote shell command.
- Run it after step 4 completes.
- Use Bash tool to run:

```bash
ssh -i "$HOME/.ssh/niro_server_ed25519" \
  -o IdentitiesOnly=yes \
  -o BatchMode=yes \
  -o ConnectTimeout=10 \
  root@106.53.11.158 '$ARGUMENTS'
```

For project-specific commands, prefer arguments that explicitly enter the project directory:

```bash
"cd /home/app/niro && <command>"
```

### 6. Safety rules

- Do not run `git pull`, `docker compose up`, `docker compose build`, or any deployment command by default.
- Do not start, stop, restart, rebuild, recreate, or remove containers unless the user explicitly includes that operation in `$ARGUMENTS` and the current conversation clearly authorizes it.
- Do not run destructive commands unless the user explicitly included them in `$ARGUMENTS` and the current conversation clearly authorizes them.
- If `$ARGUMENTS` includes high-risk operations such as `rm -rf`, `docker compose down -v`, `git reset --hard`, database deletion, or production restart, pause and ask for confirmation before executing.
- Do not echo secrets, private keys, tokens, or environment files containing credentials.
- Do not store passwords in command files or shell history.
- Do not change Git branches on the remote server unless the user explicitly asks.
- Do not run `git reset --hard`, `git clean`, or force operations to make a command succeed; report the blocker instead.

## Error Handling

If any step fails:

- Report the failed step.
- Include the key error message.
- Give one concrete next action.

Examples:

```text
SSH key missing: ~/.ssh/niro_server_ed25519
Next step: place the private key at that path or regenerate the key pair.
```

```text
SSH authentication failed: Permission denied (publickey,password)
Next step: verify that ~/.ssh/niro_server_ed25519.pub is present in /root/.ssh/authorized_keys on the server.
```

```text
Remote status command failed: Cannot connect to the Docker daemon
Next step: verify Docker service status on the remote server or run a narrower read-only command.
```

## Success Criteria

Success means:

- SSH returns exit code 0.
- Current remote host identity is displayed.
- Current test environment container status and listening ports are reported.
- No project pull, build, deployment, or container lifecycle operation runs by default.
- Optional extra remote command, if provided and safe, completes successfully.