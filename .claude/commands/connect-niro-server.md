---
description: 一条流水线打通 Niro test/prod 部署：检查未推送改动 → 触发 GitHub CI 构建镜像 → 服务器拉代码 + 拉镜像 + 重启
argument-hint: <test|prod> [ci|deploy]
---

# Niro 端到端部署

把一次"代码改完 → 远程跑上"的完整流水线串起来：

1. **本地检查**：working tree 干净、本地领先的 commit 推到远程
2. **触发 GitHub CI**：手动 dispatch `.github/workflows/dockerhub-manual.yml`，构建 4 个镜像并推 Docker Hub
3. **服务器部署**：SSH 到目标环境，`git pull` + `docker compose pull` + `docker compose up -d`
4. **报告状态**：`docker compose ps` 汇总最终容器状态

## 必传参数

`$ARGUMENTS` 第一个 token 必须是 `test` 或 `prod`。其他值或为空 → 立刻停止并提示用法。

| 环境  | SSH 目标               | 端口  | 私钥路径                                  | 项目目录              | Compose 文件              | CI tag      |
|------|-----------------------|------|-------------------------------------------|----------------------|---------------------------|-------------|
| test | `root@106.53.11.158`  | 62222 | `C:\Users\24160\.ssh\ThinkBook.pem`       | `/home/app/niro`     | `docker-compose.test.yml` | `test-latest` |
| prod | `root@119.29.200.243` | 62222 | `C:\Users\24160\.ssh\niro_server_ed25519` | `/home/workspace/niro` | `docker-compose.yml`    | `latest`    |

## 可选模式（第二个 token）

- **缺省** → 完整流程：本地检查 → CI → 部署
- **`ci`** → 只到 CI 完成为止，**不**做服务器部署
- **`deploy`** → 跳过本地检查与 CI，直接走服务器部署（适合"CI 已经跑完，现在只想部署"）

用法示例：

```bash
/connect-niro-server test           # 完整流程
/connect-niro-server prod           # 完整流程（执行前必须二次确认）
/connect-niro-server test deploy    # 仅部署，跳过 push/CI
/connect-niro-server prod ci        # 仅触发 CI，不部署
```

## 安全约束

- **prod 任何写操作前必须二次确认**：在主会话明确说明"即将对生产环境推送代码 / 触发 CI / 重启容器"，等用户回复确认后再继续。
- 不自动 `git commit`，不修改本地文件；只检查状态、最多在用户允许下 `git push`。
- 不打印私钥、token、`.env` 内容；workflow 用 GitHub Secrets，不在本地缓存凭据。
- 服务器侧只允许下文列出的命令；禁止 `git reset --hard` / `git clean -f` / `docker compose down -v` / `--force-recreate` / `--build` / `rm -rf` 等。
- 不切换远程或本地 Git 分支；不在远程 stash。
- 任何 step 失败 → 立刻停止后续步骤，原样报错，**不**绕过、**不**重试到成功。

## 执行步骤

所有 SSH 调用统一使用 `-o IdentitiesOnly=yes -o BatchMode=yes -o ConnectTimeout=10`，下文用 `$SSH_OPTS` 代指。所有 `gh` 调用要求本地已 `gh auth login`，未登录则让用户手动登录后再跑。

### 0. 解析参数

第一个参数 → 绑定环境（同上表）。第二个参数 → 决定流程：

- 空 → `FULL` 模式（执行 §1~§5）
- `ci` → 执行 §1~§3，跳过 §4~§5
- `deploy` → 跳过 §1~§3，直接执行 §4~§5

第一个参数缺失/非法 → 停止，输出用法示例。第二个参数非法（不在 `ci` / `deploy` 集合内）→ 停止并提示。

### 1. 本地 git 状态检查（FULL / ci）

并行跑：

```bash
git status --short
git rev-parse --abbrev-ref HEAD
git rev-list --left-right --count "@{upstream}...HEAD" 2>/dev/null
```

判定：

- **working tree 有未提交改动**（`git status --short` 非空）→ **停止**，原样列出改动文件，提示用户先自己 commit 再重跑本命令。**不要**代为 commit。
- **detached HEAD** → 停止，让用户先 checkout 一个分支。
- **本地落后远程**（`left-right` 第一位 > 0）→ 停止，提示用户先 `git pull --rebase` 再重跑。
- **本地领先远程**（第二位 > 0）→ 进入 §2 询问推送。
- **完全同步** → 跳过 §2，直接进入 §3。

### 2. 推送到远程（FULL / ci，仅当本地领先时）

主会话明确告知 "即将推送 N 个本地 commit 到 `origin/<branch>`"，等待用户确认。**prod 环境**额外强调"接下来会触发生产 CI 与生产部署"。

确认后执行：

```bash
git push
```

禁止 `--force` / `--force-with-lease`，禁止 push 到非 upstream 分支。push 失败 → 原样报错并停止，**不**尝试 rebase/reset。

### 3. 触发 GitHub CI 并等待完成（FULL / ci）

```bash
gh workflow run dockerhub-manual.yml -f target_env=<test|prod>
```

`target_env` 取本次第一个参数。触发后等待 ~5 秒，再拿最新 run id：

```bash
RUN_ID=$(gh run list --workflow=dockerhub-manual.yml --limit 1 --json databaseId --jq '.[0].databaseId')
```

阻塞等待该 run 跑完：

```bash
gh run watch "$RUN_ID" --exit-status
```

预期：exit code = 0，且 `gh run view $RUN_ID --json conclusion --jq '.conclusion'` 返回 `success`。

失败处理：

- workflow 触发失败（auth / 权限） → 停止，建议 `gh auth status` 与仓库 actions 权限。
- 构建/推送失败 → 停止，跑一次 `gh run view $RUN_ID --log-failed | tail -200` 截取关键失败行，**不**整段贴回上下文；给出"查看完整日志请用 `gh run view $RUN_ID --log`"提示。
- 超时（默认 30 分钟为上限，OCR 镜像构建较慢，请耐心等待）→ 让用户决定是继续等待还是中止。

CI 成功后记录：

- `${ENV}-${SHA7}` 标签（自动生成）
- `latest` 或 `test-latest` 标签（已被 workflow 覆盖到最新构建）

如果模式是 `ci`，输出 CI 报告后结束，**不**进入 §4。

### 4. prod 部署二次确认（FULL / deploy，仅 prod）

环境为 `prod` 时，在主会话明确输出：

> 即将对 **生产环境 `root@119.29.200.243`** 执行：`cd /home/workspace/niro && git fetch && git pull --ff-only && docker compose pull && docker compose up -d`。是否继续？请回复 `确认` 后再继续。

未确认前禁止进入 §5。

### 5. 服务器侧部署（FULL / deploy）

先检查私钥：

```bash
test -f "$SSH_KEY"
```

不存在 → 停止，报告路径，让用户放置或生成密钥。

依次执行（逐个 SSH 调用，方便定位失败点）：

```bash
# 5.1 拉代码（fast-forward only）
ssh -i "$SSH_KEY" -p 62222 $SSH_OPTS "$SSH_TARGET" \
  "cd $PROJECT_DIR && git fetch --prune && git pull --ff-only"

# 5.2 拉镜像
ssh -i "$SSH_KEY" -p 62222 $SSH_OPTS "$SSH_TARGET" \
  "cd $PROJECT_DIR && docker compose -f $COMPOSE_FILE pull"

# 5.3 启动/重启
ssh -i "$SSH_KEY" -p 62222 $SSH_OPTS "$SSH_TARGET" \
  "cd $PROJECT_DIR && docker compose -f $COMPOSE_FILE up -d"

# 5.4 查最终状态
ssh -i "$SSH_KEY" -p 62222 $SSH_OPTS "$SSH_TARGET" \
  "cd $PROJECT_DIR && docker compose -f $COMPOSE_FILE ps"
```

各步失败处理：

- 5.1 非 fast-forward / 冲突 → 停止并报告，**不**自动 `reset/merge`。
- 5.2 镜像 pull 失败（拉不到 latest / 网络）→ 停止，建议检查 Docker Hub 是否真有最新 tag、远程网络是否正常。
- 5.3 启动失败 → 停止，建议 `docker compose -f $COMPOSE_FILE logs <service> --tail=200`。
- 不要追加 `--force-recreate` / `--build` / `--remove-orphans`，除非用户在当前会话明确要求。

### 6. 汇总报告（中文）

无论从哪个模式结束，最终给一个简洁报告，至少包含：

- 模式：`FULL` / `ci` / `deploy`
- 目标环境：`test` / `prod`
- 本地 git：是否推送、推送了哪些 commit（短 hash + 标题）
- CI：run id、最终结论、关键镜像 tag（如 `wkelai/niro-web:test-latest`、`wkelai/niro-web:test-<sha>`）
- 远程 git：是否更新到最新、最新 commit hash + 标题
- 镜像 pull：被更新的镜像列表（无则写"无镜像更新"）
- `compose up -d`：哪些容器 recreate、哪些保持运行
- 当前 `compose ps` 表格

报告**只保留摘要**，不残留完整 CI/SSH 原始日志。

## 错误处理

任意一步失败：

- 立即停止后续步骤。
- 报告失败阶段名 + 关键错误（不要整段日志，截取关键行）。
- 给一个**具体**的下一步建议。

示例：

```text
[§1] working tree 有未提交改动：
  M niro-server/niro-web/src/main/java/.../FooService.java
  ?? niro-server/.../BarMapper.java
建议：先把这些改动 commit（不要让本命令代为提交），再重跑 /connect-niro-server <env> 或 /connect-niro-server <env> deploy 跳过 CI 直接部署。
```

```text
[§3] gh run watch 失败：run 12345678 conclusion=failure
关键失败：niro-web 构建阶段 `mvn package` exit 1
建议：gh run view 12345678 --log-failed 查看完整失败日志；不要进入部署阶段。
```

```text
[§5.1] git pull 非 fast-forward：error: Your local changes to the following files would be overwritten by merge: docker-compose.yml
建议：登录远程人工排查改动来源，不要在本命令里覆盖。
```

## 成功标准

- 本地：working tree 干净，本地分支与远程同步。
- CI：workflow run conclusion = `success`，所有镜像 tag 推送完成。
- 远程：项目目录 fast-forward 到最新 commit；镜像更新到 latest；`compose up -d` 成功；`compose ps` 无 `Exit` / `Restarting` 异常。
- 主会话只保留摘要报告，不残留完整日志。
