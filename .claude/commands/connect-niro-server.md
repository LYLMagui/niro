---
description: 连接 Niro 远程服务器并查看指定环境状态
argument-hint: "<test|prod|baidu> [extra-remote-command]"
---

# Connect Niro Server

连接 Niro 远程服务器，按用户指定连接测试环境 `root@106.53.11.158:62222`、生产环境 `root@119.29.200.243:62222` 或百度云环境 `root@106.12.50.74:62222`。`test` 与 `prod` 默认使用本机私钥 `C:\Users\24160\.ssh\ThinkBook.pem`，`baidu` 默认使用本机私钥 `C:\Users\24160\.ssh\niro_server_ed25519`。这个命令的主要功能是建立连接、确认远程主机状态、查看当前指定环境容器与监听端口；默认不拉取项目、不构建镜像、不启动或重启容器。

## What This Command Does

1. **检查 SSH 私钥**：确认 `~/.ssh/niro_server_ed25519` 存在，不读取私钥内容。
2. **检查远程连通性**：通过 SSH 执行 `hostname && whoami && pwd`。
3. **查看指定环境状态**：输出 `niro-*`、`xxl-job-*`、`rmq*`、Redis、数据库等相关容器状态与端口。
4. **查看监听端口**：输出远程服务器当前 TCP 监听端口，便于判断本地连接指定环境需要开放哪些端口。
5. **执行可选远程命令**：如果 `$ARGUMENTS` 非空，则在连接检查后执行用户传入的额外远程命令。

## Usage

```bash
/connect-niro-server test
/connect-niro-server prod
/connect-niro-server baidu
/connect-niro-server test "docker logs --tail 100 niro-web-test"
/connect-niro-server prod "docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'"
/connect-niro-server test "cd /home/app/niro && git status --short"
/connect-niro-server prod "cd /home/workspace/niro && git status --short"
/connect-niro-server baidu "docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'"
```

## Implementation Steps

When this command is invoked:

### 1. Prepare connection settings

Require the first argument to identify the target environment:

- `test`: 测试环境，连接 `root@106.53.11.158:62222`
- `prod`: 生产环境，连接 `root@119.29.200.243:62222`
- `baidu`: 百度云环境，连接 `root@106.12.50.74:62222`

If the first argument is missing or is not `test` / `prod` / `baidu`:

- Stop immediately.
- Ask the user to specify the target server.
- Show examples: `/connect-niro-server test`、`/connect-niro-server prod` and `/connect-niro-server baidu`.

Choose `SSH_TARGET` from the first argument:

```bash
SSH_TEST_KEY="C:\Users\24160\.ssh\ThinkBook.pem"
SSH_PROD_KEY="C:\Users\24160\.ssh\ThinkBook.pem"
SSH_BAIDU_KEY="C:\Users\24160\.ssh\niro_server_ed25519"
SSH_TEST_TARGET="root@106.53.11.158"
SSH_PROD_TARGET="root@119.29.200.243"
SSH_BAIDU_TARGET="root@106.12.50.74"
SSH_KEY="$SSH_TEST_KEY"       # when the first argument is test
SSH_KEY="$SSH_PROD_KEY"       # when the first argument is prod
SSH_KEY="$SSH_BAIDU_KEY"      # when the first argument is baidu
SSH_TARGET="$SSH_TEST_TARGET" # when the first argument is test
SSH_TARGET="$SSH_PROD_TARGET" # when the first argument is prod
SSH_TARGET="$SSH_BAIDU_TARGET" # when the first argument is baidu
SSH_TEST_PROJECT_DIR="/home/app/niro"
SSH_PROD_PROJECT_DIR="/home/workspace/niro"
REMOTE_PROJECT_DIR="$SSH_TEST_PROJECT_DIR"  # when the first argument is test
REMOTE_PROJECT_DIR="$SSH_PROD_PROJECT_DIR"  # when the first argument is prod
REMOTE_PROJECT_DIR=""                       # when the first argument is baidu
SSH_OPTS="-i $SSH_KEY -p 62222 -o IdentitiesOnly=yes -o BatchMode=yes -o ConnectTimeout=10"
```

Treat all remaining arguments after `test` / `prod` / `baidu` as the optional extra remote command.

Do not print private key content. Do not ask the user for passwords.

### 2. Check private key file

Use Bash tool to run:

```bash
test -f "$SSH_KEY"
```

If the file does not exist:

- Stop immediately.
- Report that the SSH private key is missing.
- Tell the user to generate or place the selected key at the path configured by `SSH_KEY`.

### 3. Run connection check

Use Bash tool to run:

```bash
ssh -i "$SSH_KEY" \
  -p 62222 \
  -o IdentitiesOnly=yes \
  -o BatchMode=yes \
  -o ConnectTimeout=10 \
  "$SSH_TARGET" 'hostname && whoami && pwd'
```

Expected successful output includes the remote hostname, current user, and current directory, for example:

```text
<remote-hostname>
root
/root
```

If SSH exits non-zero:

- Report the exact SSH error.
- Do not retry with password.
- Suggest checking key binding, server security group, SSH service, and `authorized_keys`.

### 4. Inspect current selected environment status

Use Bash tool to run this read-only remote status command:

```bash
ssh -i "$SSH_KEY" \
  -p 62222 \
  -o IdentitiesOnly=yes \
  -o BatchMode=yes \
  -o ConnectTimeout=10 \
  "$SSH_TARGET" \
  'hostname && whoami && docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" && printf "\nLISTEN PORTS\n" && ss -lntp'
```

Expected successful outcome:

- SSH returns exit code 0.
- The output shows current container status and exposed ports.
- No project files are changed.
- No containers are built, started, stopped, restarted, or recreated.

### 5. Execute optional extra remote command

If the optional extra remote command is empty:

- Stop after connection check and status output.
- Report that the server connection was successful and current selected environment status was displayed.

If the optional extra remote command is not empty:

- Treat the remaining arguments after `test` / `prod` as an extra remote shell command.
- Run it after step 4 completes.
- Use Bash tool to run:

```bash
ssh -i "$SSH_KEY" \
  -p 62222 \
  -o IdentitiesOnly=yes \
  -o BatchMode=yes \
  -o ConnectTimeout=10 \
  "$SSH_TARGET" '$ARGUMENTS'
```

For project-specific commands, prefer arguments that explicitly enter the selected environment project directory when that environment has one:

- `test`: `/home/app/niro`
- `prod`: `/home/workspace/niro`
- `baidu`: 无项目目录，仅执行用户显式提供的远程命令

```bash
"cd /home/app/niro && <command>"
"cd /home/workspace/niro && <command>"
"<command>"
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
- Current selected environment container status and listening ports are reported.
- No project pull, build, deployment, or container lifecycle operation runs by default.
- Optional extra remote command, if provided and safe, completes successfully.