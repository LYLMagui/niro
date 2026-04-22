# CS2 商品初始化数据源切换

日期：2026-04-22  
状态：已落地

## 1. 目标

将 PostgreSQL initdb 阶段的 CS2 商品初始化从“启动时访问 GitHub 下载 JSON”改为“读取仓库内本地固定文件名 JSON”。

## 2. 已确认边界

1. test 与 prod 两套 docker compose 都使用同一套本地初始化规则。
2. 后续数据更新通过替换同名文件完成，不通过 compose 改路径或改文件名完成。
3. 本地文件名直接沿用 GitHub 原始下载文件名。
4. 不在本地启动 docker 做验证。

## 3. 落地规则

### 3.1 文件目录

本地 JSON 固定放在：

- `docker/postgres/initdb/seed-data/`

### 3.2 文件命名

必须使用 upstream URL 对应的原始文件名，例如：

- `skins_not_grouped.json`
- `crates.json`
- `keys.json`
- `stickers.json`
- `agents.json`
- `patches.json`
- `music_kits.json`
- `graffiti.json`

### 3.3 脚本行为

- `docker/postgres/initdb/20-bootstrap/001-download-cs2-catalog.sh` 不再执行下载。
- 脚本按 `seed-data/cs2-goods-source-manifest.tsv` 中记录的 upstream URL 推导文件名。
- 脚本校验本地 JSON 是否存在且非空。
- 校验通过后，将本地文件复制到 `/tmp/niro-cs2-seed/{dataset}.json`。
- `docker/postgres/initdb/20-bootstrap/002-import-cs2-goods.sql` 继续按既有 `{dataset}.json` 文件名导入，无需改动。

### 3.4 compose 统一规则

- `docker-compose.test.yml`
- `docker-compose.prod.yml`

两者继续统一挂载：

- `./docker/postgres/initdb:/docker-entrypoint-initdb.d:ro`

不再为 PostgreSQL 构建或运行阶段保留 GitHub 下载所需的代理构建参数与环境变量。

## 4. 替换方式

后续更新数据时，只做以下动作：

1. 下载新的 JSON 文件。
2. 保持文件名不变。
3. 覆盖到 `docker/postgres/initdb/seed-data/`。

不需要修改 compose，不需要修改 SQL，不需要修改初始化脚本映射关系。

## 5. 风险与约束

1. 缺少任意一个清单文件对应的本地 JSON 时，初始化会直接失败。
2. JSON 文件为空时，初始化会直接失败。
3. 因为改为本地文件模式，数据新鲜度依赖人工替换文件，不再依赖远端仓库实时内容。

## 6. 验证方式

本次只做静态验证：

1. 确认 test/prod compose 继续共用同一 initdb 挂载目录。
2. 确认初始化脚本不再包含下载逻辑。
3. 确认导入 SQL 仍读取 `/tmp/niro-cs2-seed/{dataset}.json`。
4. 确认清单文件已声明“本地文件名沿用 upstream 文件名”的规则。
