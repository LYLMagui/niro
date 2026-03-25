# Niro Ace 架构设计

## 1. 目标

`niro-ace` 是一个自用的语义搜索引擎后端，技术栈为 `PostgreSQL + Qdrant + FastAPI`，统一承载代码索引库与文档索引库。

本阶段目标：

- 支持多来源文档入库
- 支持统一切片与向量索引
- 支持元数据过滤 + 语义检索
- 支持代码与文档统一检索
- 支持增量更新与全量重建

不做的事：

- 不在第一阶段构建完整 RAG 平台
- 不引入复杂分布式事务
- 不提前做多租户和权限系统

---

## 2. 总体架构

### 2.1 架构原则

- PostgreSQL 是主事实源
- Qdrant 是可重建索引层
- FastAPI 是统一控制面和检索入口
- 代码与文档统一建模，但分开解析与切片策略

### 2.2 架构图

```text
                    ┌──────────────────────┐
                    │      Client / CLI     │
                    └──────────┬───────────┘
                               │ HTTP
                               ▼
                    ┌──────────────────────┐
                    │       FastAPI         │
                    │  ingest / search API  │
                    └───────┬───────┬──────┘
                            │       │
              metadata/job  │       │ vector search
                            ▼       ▼
                 ┌────────────────┐  ┌────────────────┐
                 │   PostgreSQL   │  │     Qdrant     │
                 │ metadata/store │  │ vector store   │
                 └───────┬────────┘  └────────┬───────┘
                         │                    │
                         └──────┬─────────────┘
                                ▼
                     ┌──────────────────────┐
                     │   Indexing Worker     │
                     │ parse/chunk/embed/upsert │
                     └──────────────────────┘
```

---

## 3. 领域模型

### 3.1 source

表示一个数据源。

支持类型：

- `git_repo`
- `local_dir`
- `manual_upload`
- `web_doc`

职责：

- 描述数据来源
- 保存根路径或仓库地址
- 作为索引任务的归属单元

### 3.2 document

表示一个可索引文档实体。

文档可以是：

- 代码文件
- Markdown 文档
- PDF 解析结果
- HTML 清洗结果
- 普通文本

职责：

- 保存原始文档级元数据
- 保存版本和校验信息
- 作为 chunk 的父级对象

### 3.3 chunk

表示检索最小单元。

职责：

- 保存可检索文本
- 记录结构位置信息
- 建立 PG 与 Qdrant 的映射关系

### 3.4 indexing_job

表示一次索引任务。

职责：

- 驱动全量或增量索引
- 记录执行状态
- 支持失败重试与排错

---

## 4. 统一建模策略

统一抽象为：

- `source`
- `document`
- `chunk`

通过以下字段区分类型：

- `content_type`：`code | markdown | pdf | html | text`
- `chunk_type`：`symbol | paragraph | section | snippet`

这意味着：

- 存储模型统一
- 检索入口统一
- parser 和 chunker 分开实现

这是比“代码一套表、文档一套表”更稳的方式。

---

## 5. PostgreSQL 设计

### 5.1 数据库实现约束

数据库层采用 **方案 C：先写规范化 SQL schema，再映射 SQLAlchemy 2.x 模型，Pydantic 单独负责请求与响应模型**。

SQL 规范如下：

- 所有表必须有表注释
- 所有字段必须有字段注释
- 所有字段必须 `not null`
- 所有字段必须提供默认值
- 所有外键列和高频过滤列必须建立合适索引
- 主键统一使用 `bigint generated always as identity`
- SQL DDL 作为数据库真规范，ORM 模型必须服从 `docs/schema.sql`
- 统一建模为 `source -> document -> chunk`
- 默认检索范围限定为当前 `workspace + project`，只有显式指定时才允许全局搜索

### 5.2 表清单

建议核心表：

- `ace_source`
- `ace_document`
- `ace_chunk`
- `ace_indexing_job`
- `ace_search_log`

### 5.3 字段建议

#### `ace_source`

- `id bigint generated always as identity primary key`
- `workspace_key text not null default ''`
- `project_key text not null default ''`
- `repo_name text not null default ''`
- `branch_name text not null default ''`
- `name text not null default ''`
- `source_type text not null default ''`
- `root_uri text not null default ''`
- `status text not null default 'active'`
- `metadata jsonb not null default '{}'::jsonb`
- `created_at timestamptz not null default now()`
- `updated_at timestamptz not null default now()`

#### `ace_document`

- `id bigint generated always as identity primary key`
- `source_id bigint not null references ace_source(id)`
- `workspace_key text not null default ''`
- `project_key text not null default ''`
- `repo_name text not null default ''`
- `branch_name text not null default ''`
- `external_id text not null default ''`
- `title text not null default ''`
- `path text not null default ''`
- `content_type text not null default ''`
- `language text not null default ''`
- `checksum text not null default ''`
- `version text not null default ''`
- `status text not null default 'pending'`
- `metadata jsonb not null default '{}'::jsonb`
- `created_at timestamptz not null default now()`
- `updated_at timestamptz not null default now()`

#### `ace_chunk`

- `id bigint generated always as identity primary key`
- `document_id bigint not null references ace_document(id)`
- `workspace_key text not null default ''`
- `project_key text not null default ''`
- `repo_name text not null default ''`
- `branch_name text not null default ''`
- `chunk_index integer not null default 0`
- `chunk_type text not null default ''`
- `content_type text not null default ''`
- `content text not null default ''`
- `token_count integer not null default 0`
- `start_offset integer not null default 0`
- `end_offset integer not null default 0`
- `heading_path text not null default ''`
- `symbol_name text not null default ''`
- `symbol_kind text not null default ''`
- `metadata jsonb not null default '{}'::jsonb`
- `embedding_model text not null default ''`
- `embedding_version text not null default ''`
- `qdrant_point_id text not null default ''`
- `created_at timestamptz not null default now()`

#### `ace_indexing_job`

- `id bigint generated always as identity primary key`
- `source_id bigint not null references ace_source(id)`
- `workspace_key text not null default ''`
- `project_key text not null default ''`
- `repo_name text not null default ''`
- `branch_name text not null default ''`
- `job_type text not null default ''`
- `status text not null default 'pending'`
- `error_message text not null default ''`
- `payload jsonb not null default '{}'::jsonb`
- `started_at timestamptz not null default now()`
- `finished_at timestamptz not null default now()`
- `created_at timestamptz not null default now()`

#### `ace_search_log`

- `id bigint generated always as identity primary key`
- `workspace_key text not null default ''`
- `project_key text not null default ''`
- `repo_name text not null default ''`
- `branch_name text not null default ''`
- `query_text text not null default ''`
- `search_scope text not null default 'project'`
- `result_count integer not null default 0`
- `latency_ms integer not null default 0`
- `metadata jsonb not null default '{}'::jsonb`
- `created_at timestamptz not null default now()`

### 5.4 索引建议

#### `ace_source`

- `unique (source_type, root_uri)`
- `create index on ace_source (workspace_key, project_key)`
- `create index on ace_source (repo_name, branch_name)`

#### `ace_document`

- `create index on ace_document (source_id)`
- `create unique index on ace_document (source_id, path)`
- `create index on ace_document (workspace_key, project_key)`
- `create index on ace_document (repo_name, branch_name)`
- `create index on ace_document (content_type)`
- `create index on ace_document (status)`
- `create index on ace_document (checksum)`

#### `ace_chunk`

- `create index on ace_chunk (document_id)`
- `create unique index on ace_chunk (document_id, chunk_index)`
- `create index on ace_chunk (workspace_key, project_key)`
- `create index on ace_chunk (repo_name, branch_name)`
- `create index on ace_chunk (content_type, chunk_type)`
- `create index on ace_chunk (symbol_name)`
- `create index on ace_chunk (embedding_model)`

#### `ace_indexing_job`

- `create index on ace_indexing_job (source_id)`
- `create index on ace_indexing_job (workspace_key, project_key)`
- `create index on ace_indexing_job (status, created_at)`

#### `ace_search_log`

- `create index on ace_search_log (workspace_key, project_key)`
- `create index on ace_search_log (created_at)`

### 5.5 JSONB 边界

`jsonb` 只用于：

- 扩展属性
- 自定义标签
- 解析器附加字段
- 调试信息

以下字段不应放入 `jsonb`：

- `workspace_key`
- `project_key`
- `repo_name`
- `branch_name`
- `path`
- `content_type`
- `language`
- `status`
- `chunk_type`

这些字段属于高频过滤列，必须结构化。

---

## 6. Qdrant 设计

### 6.1 Collection 策略

第一阶段建议只使用一个 collection：`ace_chunk`

原因：

- 目标是统一搜索
- 代码和文档结果需要统一召回
- 单 collection + payload filter 已足够覆盖当前需求

### 6.2 Point payload 建议

每个 point 对应一个 chunk。

payload 字段建议：

- `chunk_id`
- `document_id`
- `source_id`
- `content_type`
- `chunk_type`
- `language`
- `path`
- `title`
- `symbol_name`
- `symbol_kind`
- `heading_path`
- `version`
- `tags`

### 6.3 一致性原则

- PostgreSQL 是主事实源
- Qdrant 允许失败后重建
- 不做跨库强事务
- 每个 chunk 保留 `qdrant_point_id`

---

## 7. 解析与切片策略

### 7.1 代码索引

代码的关键不是“按段落切”，而是“按符号边界切”。

建议顺序：

1. 按函数 / 类 / 方法切片
2. 超长符号再细分
3. 记录：
   - 文件路径
   - 符号名
   - 符号类型
   - 起止位置
   - 所属模块

### 7.2 文档索引

文档的关键是结构层级。

建议顺序：

1. 按标题 section 切
2. 再按段落 / 列表块细分
3. 超长 section 再二次切片
4. 记录：
   - 文档标题
   - 标题路径
   - 所属章节

### 7.3 结论

统一存储，分开切片。

不要写一个万能切片器。那是坏味道。

---

## 8. 检索流程

### 8.1 基础流程

1. 接收 query
2. 生成 query embedding
3. Qdrant 执行 top-k 相似度检索
4. 根据 payload 做过滤：
   - `source_id`
   - `content_type`
   - `language`
   - `path prefix`
   - `tags`
5. 用 `chunk_id` 回查 PG
6. 返回统一结果结构

### 8.2 返回结构建议

- `score`
- `content`
- `content_type`
- `chunk_type`
- `title`
- `path`
- `source_name`
- `symbol_name`
- `metadata`

### 8.3 第一阶段不做

- 不做 rerank
- 不做 hybrid search
- 不做 query rewrite
- 不做 graph retrieval

先把主干链路打通。

---

## 9. 索引任务设计

### 9.1 全量重建

适合：

- 初次导入
- parser 升级
- embedding 模型切换

流程：

1. 扫描 source
2. 生成 document 列表
3. 基于 checksum 比较变更
4. 对新增或变更文档重建 chunk
5. 写入 PG
6. upsert 到 Qdrant
7. 清理失效文档和孤儿 chunk

### 9.2 增量更新

适合：

- 仓库变更
- 文档新增或修改

第一阶段只建议做基于 `checksum` 的增量。

不要提前上 CDC、事件总线或复杂 watch 机制。

---

## 10. 应用内部分层

建议目录：

```text
niro-ace/
  app/
    api/
    schemas/
    services/
    domain/
    repositories/
    parsers/
    chunkers/
    embeddings/
    workers/
    core/
  migrations/
  tests/
```

分层约束：

- `api/` 只处理 HTTP
- `services/` 编排业务流程
- `repositories/` 封装 PG 和 Qdrant
- `parsers/` 负责源内容解析
- `chunkers/` 负责切片
- `workers/` 负责后台索引任务
- `core/` 放配置、日志、依赖注入

---

## 11. API 最小闭环

第一阶段只保留这些接口：

### 数据源
- `POST /sources`
- `GET /sources`
- `GET /sources/{source_id}`

### 索引任务
- `POST /sources/{source_id}/index`
- `GET /jobs/{job_id}`

### 检索
- `POST /search`

### 调试
- `GET /documents/{document_id}`
- `GET /documents/{document_id}/chunks`

不要提前设计大量管理接口。

---

## 12. Embedding 抽象

不要把 Embedding 调用散落到业务里。

建议抽象接口：

- `embed_query(text: str)`
- `embed_documents(texts: list[str])`

第一阶段只要求接口稳定。

实现可先接一个 provider，后续再替换成本地模型或其他服务。

---

## 13. 第一阶段交付范围

### 必做

1. `source / document / chunk / job` 核心模型
2. 本地目录与 Git 仓库两类 source
3. Markdown / text / code 文件解析
4. 统一检索 API
5. PostgreSQL 元数据持久化
6. Qdrant 向量 upsert
7. checksum 增量更新

### 延后

1. PDF 精细解析
2. HTML 清洗
3. rerank
4. hybrid search
5. symbol graph
6. WebSocket 任务进度
7. 多租户权限

---

## 14. 推荐结论

推荐方案如下：

- 统一索引模型：`document + chunk`
- 统一向量库：Qdrant 单 collection
- 统一检索入口：FastAPI `/search`
- PostgreSQL 作为主事实源
- 代码与文档分开切片
- 索引任务异步执行，失败可重试

这个方案的优点是：

- 简单
- 稳定
- 易重建
- 后续能扩展到 hybrid、rerank 和 RAG

---

## 15. 下一步

下一步建议按这个顺序推进：

1. 输出数据库 schema 草案
2. 生成 `niro-ace` 第一阶段实现计划
3. 再开始创建项目骨架与基础代码
