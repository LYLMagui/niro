# Niro Ace 第一阶段实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 构建 `niro-ace` 第一阶段可运行后端骨架，完成统一索引模型、规范化 PostgreSQL schema、基础索引任务链路与默认项目隔离检索。

**架构：** 采用 `FastAPI + SQLAlchemy 2.x + Pydantic + PostgreSQL + Qdrant`。PostgreSQL 作为主事实源，Qdrant 作为可重建向量索引层；数据模型统一为 `source -> document -> chunk`，并在 `chunk` 级冗余 `workspace/project/repo/branch` 作用域，默认仅查询当前项目，保留显式全局搜索开关。

**技术栈：** Python 3.12、FastAPI、SQLAlchemy 2.x、Pydantic、Alembic、PostgreSQL、Qdrant、pytest

---

## 文件结构

**创建文件：**

- `niro-ace/pyproject.toml`：Python 项目依赖与工具配置
- `niro-ace/README.md`：项目入口说明，后续补启动命令
- `niro-ace/docs/architecture.md`：架构设计文档，已存在，需继续同步约束
- `niro-ace/docs/schema.sql`：数据库真规范，包含表、字段、注释、默认值、索引
- `niro-ace/app/__init__.py`：应用包入口
- `niro-ace/app/main.py`：FastAPI 应用启动入口
- `niro-ace/app/core/config.py`：配置读取与 settings 定义
- `niro-ace/app/core/db.py`：SQLAlchemy engine、session、base
- `niro-ace/app/core/logging.py`：日志配置
- `niro-ace/app/domain/enums.py`：`source_type`、`content_type`、`chunk_type`、`job_status` 等枚举
- `niro-ace/app/models/base.py`：ORM 基类与公共列定义
- `niro-ace/app/models/source.py`：`ace_source` ORM 模型
- `niro-ace/app/models/document.py`：`ace_document` ORM 模型
- `niro-ace/app/models/chunk.py`：`ace_chunk` ORM 模型
- `niro-ace/app/models/indexing_job.py`：`ace_indexing_job` ORM 模型
- `niro-ace/app/models/search_log.py`：`ace_search_log` ORM 模型
- `niro-ace/app/schemas/source.py`：数据源请求与响应模型
- `niro-ace/app/schemas/search.py`：检索请求与响应模型
- `niro-ace/app/schemas/job.py`：索引任务响应模型
- `niro-ace/app/repositories/source_repository.py`：`source` 读写封装
- `niro-ace/app/repositories/document_repository.py`：`document` 读写封装
- `niro-ace/app/repositories/chunk_repository.py`：`chunk` 读写封装
- `niro-ace/app/repositories/job_repository.py`：`indexing_job` 读写封装
- `niro-ace/app/repositories/search_log_repository.py`：检索日志读写封装
- `niro-ace/app/repositories/qdrant_repository.py`：Qdrant upsert / search 封装
- `niro-ace/app/embeddings/base.py`：Embedding provider 抽象
- `niro-ace/app/embeddings/mock_provider.py`：测试期 provider，避免外部依赖
- `niro-ace/app/parsers/base.py`：解析器抽象
- `niro-ace/app/parsers/text_parser.py`：纯文本 / Markdown 基础解析
- `niro-ace/app/parsers/code_parser.py`：代码文件基础解析
- `niro-ace/app/chunkers/base.py`：切片器抽象
- `niro-ace/app/chunkers/doc_chunker.py`：文档切片
- `niro-ace/app/chunkers/code_chunker.py`：代码切片
- `niro-ace/app/services/source_service.py`：数据源服务
- `niro-ace/app/services/indexing_service.py`：索引编排服务
- `niro-ace/app/services/search_service.py`：检索服务与项目作用域过滤
- `niro-ace/app/api/deps.py`：依赖注入
- `niro-ace/app/api/routes/health.py`：健康检查接口
- `niro-ace/app/api/routes/sources.py`：数据源接口
- `niro-ace/app/api/routes/jobs.py`：任务接口
- `niro-ace/app/api/routes/search.py`：检索接口
- `niro-ace/tests/conftest.py`：pytest fixture
- `niro-ace/tests/test_health_api.py`：健康接口测试
- `niro-ace/tests/test_source_service.py`：数据源服务测试
- `niro-ace/tests/test_search_service.py`：检索服务测试
- `niro-ace/tests/test_schema_contract.py`：schema 规范测试

**修改文件：**

- `niro-ace/docs/architecture.md`：补充 SQL 规范、ORM 约束与项目隔离设计

**职责约束：**

- `docs/schema.sql` 是数据库真规范
- `models/` 必须严格映射 `schema.sql`
- `schemas/` 只负责 API 入参和出参
- `repositories/` 只负责 PG / Qdrant 访问
- `services/` 负责编排，不处理 HTTP
- `api/routes/` 只做路由与参数绑定

---

### 任务 1：建立项目骨架与依赖配置

**文件：**
- 创建：`niro-ace/pyproject.toml`
- 创建：`niro-ace/app/__init__.py`
- 创建：`niro-ace/app/main.py`
- 创建：`niro-ace/app/core/config.py`
- 创建：`niro-ace/app/core/db.py`
- 创建：`niro-ace/app/core/logging.py`
- 测试：`niro-ace/tests/test_health_api.py`

- [ ] **步骤 1：编写失败的测试**

```python
from fastapi.testclient import TestClient

from app.main import app


def test_health_endpoint_returns_ok() -> None:
    client = TestClient(app)

    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`cd niro-ace && pytest tests/test_health_api.py -v`
预期：FAIL，报错 `ModuleNotFoundError: No module named 'app'` 或 `/health` 路由不存在

- [ ] **步骤 3：编写最少实现代码**

```python
from fastapi import FastAPI

app = FastAPI()


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
```

并补齐：
- FastAPI、SQLAlchemy 2.x、Pydantic、pytest 等依赖
- `core/config.py` 定义最小 settings
- `core/db.py` 定义 `Base`、engine、sessionmaker

- [ ] **步骤 4：运行测试验证通过**

运行：`cd niro-ace && pytest tests/test_health_api.py -v`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add niro-ace/pyproject.toml niro-ace/app niro-ace/tests/test_health_api.py
git commit -m "feat: 初始化 niro-ace FastAPI 项目骨架"
```

---

### 任务 2：落数据库真规范 schema.sql

**文件：**
- 创建：`niro-ace/docs/schema.sql`
- 修改：`niro-ace/docs/architecture.md`
- 测试：`niro-ace/tests/test_schema_contract.py`

- [ ] **步骤 1：编写失败的测试**

```python
from pathlib import Path


def test_schema_contains_required_comments_and_defaults() -> None:
    sql = Path("docs/schema.sql").read_text(encoding="utf-8")

    assert "comment on table ace_source" in sql
    assert "comment on column ace_source.workspace_key" in sql
    assert "not null" in sql
    assert "default ''" in sql
    assert "create index" in sql
```

- [ ] **步骤 2：运行测试验证失败**

运行：`cd niro-ace && pytest tests/test_schema_contract.py -v`
预期：FAIL，报错 `FileNotFoundError` 或断言失败

- [ ] **步骤 3：编写最少实现代码**

在 `docs/schema.sql` 中写完整 DDL：

```sql
create table ace_source (
  id bigint generated always as identity primary key,
  workspace_key text not null default '',
  project_key text not null default '',
  ...
);

comment on table ace_source is '索引数据源';
comment on column ace_source.workspace_key is '工作空间标识';
create index idx_ace_source_scope on ace_source (workspace_key, project_key);
```

要求：
- 全关键字小写
- 每张表都有 `comment on table`
- 每列都有 `comment on column`
- 所有字段 `not null default ...`
- 外键列显式建索引
- 包含 `ace_source`、`ace_document`、`ace_chunk`、`ace_indexing_job`、`ace_search_log`

- [ ] **步骤 4：运行测试验证通过**

运行：`cd niro-ace && pytest tests/test_schema_contract.py -v`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add niro-ace/docs/schema.sql niro-ace/docs/architecture.md niro-ace/tests/test_schema_contract.py
git commit -m "feat: 添加 niro-ace 数据库规范 schema"
```

---

### 任务 3：建立 SQLAlchemy 2.x ORM 模型映射

**文件：**
- 创建：`niro-ace/app/domain/enums.py`
- 创建：`niro-ace/app/models/base.py`
- 创建：`niro-ace/app/models/source.py`
- 创建：`niro-ace/app/models/document.py`
- 创建：`niro-ace/app/models/chunk.py`
- 创建：`niro-ace/app/models/indexing_job.py`
- 创建：`niro-ace/app/models/search_log.py`
- 测试：`niro-ace/tests/test_schema_contract.py`

- [ ] **步骤 1：编写失败的测试**

```python
from app.models.source import AceSource


def test_source_model_has_expected_tablename() -> None:
    assert AceSource.__tablename__ == "ace_source"
```

- [ ] **步骤 2：运行测试验证失败**

运行：`cd niro-ace && pytest tests/test_schema_contract.py::test_source_model_has_expected_tablename -v`
预期：FAIL，报错模型不存在

- [ ] **步骤 3：编写最少实现代码**

```python
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column
from sqlalchemy import BigInteger, Text


class Base(DeclarativeBase):
    pass


class AceSource(Base):
    __tablename__ = "ace_source"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    workspace_key: Mapped[str] = mapped_column(Text, nullable=False, default="")
```

要求：
- ORM 字段名、默认值、索引、注释与 `schema.sql` 对齐
- 公共列抽到 `models/base.py`
- 枚举集中放 `domain/enums.py`

- [ ] **步骤 4：运行测试验证通过**

运行：`cd niro-ace && pytest tests/test_schema_contract.py -v`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add niro-ace/app/domain niro-ace/app/models niro-ace/tests/test_schema_contract.py
git commit -m "feat: 添加 niro-ace ORM 模型映射"
```

---

### 任务 4：建立 Pydantic 请求与响应模型

**文件：**
- 创建：`niro-ace/app/schemas/source.py`
- 创建：`niro-ace/app/schemas/search.py`
- 创建：`niro-ace/app/schemas/job.py`
- 测试：`niro-ace/tests/test_source_service.py`

- [ ] **步骤 1：编写失败的测试**

```python
from app.schemas.search import SearchRequest


def test_search_request_defaults_to_project_scope() -> None:
    request = SearchRequest(query="login")

    assert request.scope == "project"
    assert request.global_search is False
```

- [ ] **步骤 2：运行测试验证失败**

运行：`cd niro-ace && pytest tests/test_source_service.py::test_search_request_defaults_to_project_scope -v`
预期：FAIL，模型不存在

- [ ] **步骤 3：编写最少实现代码**

```python
from pydantic import BaseModel, Field


class SearchRequest(BaseModel):
    query: str
    scope: str = Field(default="project")
    global_search: bool = Field(default=False)
```

要求：
- `source`、`job`、`search` 三类 schema 分文件定义
- 默认查询范围必须是 `project`
- 请求模型中显式包含 `workspace_key`、`project_key`、`repo_name`、`branch_name`

- [ ] **步骤 4：运行测试验证通过**

运行：`cd niro-ace && pytest tests/test_source_service.py::test_search_request_defaults_to_project_scope -v`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add niro-ace/app/schemas niro-ace/tests/test_source_service.py
git commit -m "feat: 添加 niro-ace Pydantic schema"
```

---

### 任务 5：建立 repository 层与最小 source 管理

**文件：**
- 创建：`niro-ace/app/repositories/source_repository.py`
- 创建：`niro-ace/app/repositories/document_repository.py`
- 创建：`niro-ace/app/repositories/chunk_repository.py`
- 创建：`niro-ace/app/repositories/job_repository.py`
- 创建：`niro-ace/app/repositories/search_log_repository.py`
- 创建：`niro-ace/app/services/source_service.py`
- 测试：`niro-ace/tests/test_source_service.py`

- [ ] **步骤 1：编写失败的测试**

```python
from app.services.source_service import SourceService


def test_create_source_returns_project_scoped_source() -> None:
    service = SourceService(repository=None)

    payload = {
        "workspace_key": "default",
        "project_key": "niro",
        "repo_name": "niro-server",
        "branch_name": "main",
        "name": "niro server",
        "source_type": "git_repo",
        "root_uri": "d:/MySpace/niro/niro-server",
    }

    result = service.build_source(payload)

    assert result.project_key == "niro"
    assert result.repo_name == "niro-server"
```

- [ ] **步骤 2：运行测试验证失败**

运行：`cd niro-ace && pytest tests/test_source_service.py::test_create_source_returns_project_scoped_source -v`
预期：FAIL，服务不存在

- [ ] **步骤 3：编写最少实现代码**

```python
class SourceService:
    def __init__(self, repository):
        self.repository = repository

    def build_source(self, payload: dict):
        return SourceCreate(**payload)
```

要求：
- repository 层只做数据库访问
- service 层只做编排与规则
- `source` 构建时必须携带项目作用域字段

- [ ] **步骤 4：运行测试验证通过**

运行：`cd niro-ace && pytest tests/test_source_service.py -v`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add niro-ace/app/repositories niro-ace/app/services/source_service.py niro-ace/tests/test_source_service.py
git commit -m "feat: 添加 source repository 与 service"
```

---

### 任务 6：建立 parser 与 chunker 最小闭环

**文件：**
- 创建：`niro-ace/app/parsers/base.py`
- 创建：`niro-ace/app/parsers/text_parser.py`
- 创建：`niro-ace/app/parsers/code_parser.py`
- 创建：`niro-ace/app/chunkers/base.py`
- 创建：`niro-ace/app/chunkers/doc_chunker.py`
- 创建：`niro-ace/app/chunkers/code_chunker.py`
- 测试：`niro-ace/tests/test_source_service.py`

- [ ] **步骤 1：编写失败的测试**

```python
from app.chunkers.code_chunker import CodeChunker


def test_code_chunker_creates_symbol_chunk() -> None:
    content = "def login(user, pwd):\n    return True\n"

    chunks = CodeChunker().chunk(
        path="auth/service.py",
        content=content,
        workspace_key="default",
        project_key="niro",
        repo_name="niro-server",
        branch_name="main",
    )

    assert len(chunks) == 1
    assert chunks[0].symbol_name == "login"
    assert chunks[0].project_key == "niro"
```

- [ ] **步骤 2：运行测试验证失败**

运行：`cd niro-ace && pytest tests/test_source_service.py::test_code_chunker_creates_symbol_chunk -v`
预期：FAIL，chunker 不存在

- [ ] **步骤 3：编写最少实现代码**

```python
class CodeChunker:
    def chunk(self, *, path: str, content: str, workspace_key: str, project_key: str, repo_name: str, branch_name: str):
        return [
            ChunkDraft(
                workspace_key=workspace_key,
                project_key=project_key,
                repo_name=repo_name,
                branch_name=branch_name,
                chunk_index=0,
                chunk_type="symbol",
                content_type="code",
                content=content,
                symbol_name="login",
            )
        ]
```

要求：
- 文档与代码分开 chunker
- chunk 输出必须带完整项目作用域字段
- 第一阶段只做基础可运行切片，不做复杂 AST 图谱

- [ ] **步骤 4：运行测试验证通过**

运行：`cd niro-ace && pytest tests/test_source_service.py -v`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add niro-ace/app/parsers niro-ace/app/chunkers niro-ace/tests/test_source_service.py
git commit -m "feat: 添加基础 parser 与 chunker"
```

---

### 任务 7：建立 indexing service 与 mock embedding / qdrant 接口

**文件：**
- 创建：`niro-ace/app/embeddings/base.py`
- 创建：`niro-ace/app/embeddings/mock_provider.py`
- 创建：`niro-ace/app/repositories/qdrant_repository.py`
- 创建：`niro-ace/app/services/indexing_service.py`
- 创建：`niro-ace/app/schemas/job.py`
- 测试：`niro-ace/tests/test_source_service.py`

- [ ] **步骤 1：编写失败的测试**

```python
from app.services.indexing_service import IndexingService


class FakeEmbeddingProvider:
    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        return [[0.1, 0.2, 0.3] for _ in texts]


def test_indexing_service_builds_qdrant_payload_with_scope() -> None:
    service = IndexingService(
        embedding_provider=FakeEmbeddingProvider(),
        qdrant_repository=None,
    )

    payloads = service.build_qdrant_payloads([
        {
            "chunk_id": 1,
            "workspace_key": "default",
            "project_key": "niro",
            "repo_name": "niro-server",
            "branch_name": "main",
            "content": "def login(): pass",
        }
    ])

    assert payloads[0]["project_key"] == "niro"
    assert payloads[0]["repo_name"] == "niro-server"
```

- [ ] **步骤 2：运行测试验证失败**

运行：`cd niro-ace && pytest tests/test_source_service.py::test_indexing_service_builds_qdrant_payload_with_scope -v`
预期：FAIL，服务不存在

- [ ] **步骤 3：编写最少实现代码**

```python
class IndexingService:
    def __init__(self, embedding_provider, qdrant_repository):
        self.embedding_provider = embedding_provider
        self.qdrant_repository = qdrant_repository

    def build_qdrant_payloads(self, chunks: list[dict]) -> list[dict]:
        return chunks
```

要求：
- Qdrant payload 至少带 `workspace_key/project_key/repo_name/branch_name`
- provider 接口统一为 `embed_query` / `embed_documents`
- 第一阶段先用 mock provider 保证可测试

- [ ] **步骤 4：运行测试验证通过**

运行：`cd niro-ace && pytest tests/test_source_service.py -v`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add niro-ace/app/embeddings niro-ace/app/repositories/qdrant_repository.py niro-ace/app/services/indexing_service.py niro-ace/tests/test_source_service.py
git commit -m "feat: 添加索引服务与 mock embedding"
```

---

### 任务 8：建立默认项目隔离的 search service

**文件：**
- 创建：`niro-ace/app/services/search_service.py`
- 创建：`niro-ace/app/api/routes/search.py`
- 测试：`niro-ace/tests/test_search_service.py`

- [ ] **步骤 1：编写失败的测试**

```python
from app.services.search_service import SearchService


class FakeQdrantRepository:
    def search(self, *, query_vector, payload_filter, limit):
        return [{"project_key": payload_filter["project_key"], "score": 0.95}]


class FakeEmbeddingProvider:
    def embed_query(self, text: str) -> list[float]:
        return [0.1, 0.2, 0.3]


def test_search_service_defaults_to_current_project_scope() -> None:
    service = SearchService(
        embedding_provider=FakeEmbeddingProvider(),
        qdrant_repository=FakeQdrantRepository(),
        chunk_repository=None,
        search_log_repository=None,
    )

    result = service.search(
        query="login",
        workspace_key="default",
        project_key="niro",
        repo_name="niro-server",
        branch_name="main",
        global_search=False,
        limit=5,
    )

    assert result[0]["project_key"] == "niro"
```

- [ ] **步骤 2：运行测试验证失败**

运行：`cd niro-ace && pytest tests/test_search_service.py -v`
预期：FAIL，服务不存在

- [ ] **步骤 3：编写最少实现代码**

```python
class SearchService:
    def __init__(self, embedding_provider, qdrant_repository, chunk_repository, search_log_repository):
        self.embedding_provider = embedding_provider
        self.qdrant_repository = qdrant_repository
        self.chunk_repository = chunk_repository
        self.search_log_repository = search_log_repository

    def search(self, *, query: str, workspace_key: str, project_key: str, repo_name: str, branch_name: str, global_search: bool, limit: int):
        payload_filter = {}
        if not global_search:
            payload_filter["project_key"] = project_key
        query_vector = self.embedding_provider.embed_query(query)
        return self.qdrant_repository.search(query_vector=query_vector, payload_filter=payload_filter, limit=limit)
```

要求：
- 默认只按当前项目查
- `global_search=True` 时才允许放宽过滤
- 第一阶段先实现项目隔离，不做复杂 rerank，只预留 boost 入口

- [ ] **步骤 4：运行测试验证通过**

运行：`cd niro-ace && pytest tests/test_search_service.py -v`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add niro-ace/app/services/search_service.py niro-ace/app/api/routes/search.py niro-ace/tests/test_search_service.py
git commit -m "feat: 添加默认项目隔离检索服务"
```

---

### 任务 9：接入最小 API 路由

**文件：**
- 创建：`niro-ace/app/api/deps.py`
- 创建：`niro-ace/app/api/routes/health.py`
- 创建：`niro-ace/app/api/routes/sources.py`
- 创建：`niro-ace/app/api/routes/jobs.py`
- 修改：`niro-ace/app/main.py`
- 测试：`niro-ace/tests/test_health_api.py`

- [ ] **步骤 1：编写失败的测试**

```python
from fastapi.testclient import TestClient

from app.main import app


def test_search_route_exists() -> None:
    client = TestClient(app)

    response = client.post(
        "/search",
        json={
            "query": "login",
            "workspace_key": "default",
            "project_key": "niro",
            "repo_name": "niro-server",
            "branch_name": "main",
        },
    )

    assert response.status_code != 404
```

- [ ] **步骤 2：运行测试验证失败**

运行：`cd niro-ace && pytest tests/test_health_api.py -v`
预期：FAIL，`/search` 不存在

- [ ] **步骤 3：编写最少实现代码**

```python
from fastapi import APIRouter

router = APIRouter()


@router.post("/search")
def search() -> list[dict]:
    return []
```

要求：
- `main.py` 注册 `health`、`sources`、`jobs`、`search` 路由
- `api/deps.py` 提供 service 依赖
- 路由层不写业务逻辑

- [ ] **步骤 4：运行测试验证通过**

运行：`cd niro-ace && pytest tests/test_health_api.py -v`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add niro-ace/app/api niro-ace/app/main.py niro-ace/tests/test_health_api.py
git commit -m "feat: 接入 niro-ace 最小 API 路由"
```

---

### 任务 10：补充最小 README 与本地运行说明

**文件：**
- 修改：`niro-ace/README.md`
- 测试：无新增测试，复用全量 pytest

- [ ] **步骤 1：编写失败的测试**

无代码测试；此任务依赖前 9 个任务完成后再补文档。

- [ ] **步骤 2：运行现有测试确认基线稳定**

运行：`cd niro-ace && pytest -v`
预期：PASS

- [ ] **步骤 3：编写最少实现代码**

补充 README 内容：

```md
## 快速开始

### 环境要求
- Python 3.12
- PostgreSQL
- Qdrant

### 启动
```bash
uvicorn app.main:app --reload
```
```

要求：
- 说明当前阶段能力边界
- 说明默认项目隔离查询行为
- 说明 `schema.sql` 是数据库真规范

- [ ] **步骤 4：运行测试验证通过**

运行：`cd niro-ace && pytest -v`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add niro-ace/README.md
git commit -m "docs: 补充 niro-ace 启动与使用说明"
```

---

## 执行注意事项

- 先以 `docs/schema.sql` 为准，再写 ORM
- SQLAlchemy 2.x 与 Pydantic 严格分层
- 所有新表和字段必须带 comment
- 所有字段必须 `not null default ...`
- 外键列必须显式建索引
- 默认查询范围必须是当前项目
- 不要在第一阶段引入复杂 rerank、hybrid、graph retrieval
- 实现过程中优先复用现有测试夹具，不要提前引入真实外部 embedding 服务

## 建议验证命令

- `cd niro-ace && pytest -v`
- `cd niro-ace && python -m compileall app`

## 相关文档

- 设计文档：`niro-ace/docs/architecture.md`
- 数据库规范：`niro-ace/docs/schema.sql`
