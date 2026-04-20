create table ace_source (
  id bigint generated always as identity primary key,
  workspace_key text not null default '',
  project_key text not null default '',
  repo_name text not null default '',
  branch_name text not null default '',
  name text not null default '',
  source_type text not null default '',
  root_uri text not null default '',
  status text not null default 'active',
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (source_type, root_uri),
  check (jsonb_typeof(metadata) = 'object'),
  check (status in ('active', 'disabled', 'deleted')),
  check (source_type in ('git_repo', 'local_dir', 'manual_upload', 'web_doc'))
);

comment on table ace_source is '索引数据源表';
comment on column ace_source.id is '主键';
comment on column ace_source.workspace_key is '工作空间标识';
comment on column ace_source.project_key is '项目标识';
comment on column ace_source.repo_name is '仓库名称';
comment on column ace_source.branch_name is '分支名称';
comment on column ace_source.name is '数据源名称';
comment on column ace_source.source_type is '数据源类型';
comment on column ace_source.root_uri is '数据源根路径或仓库地址';
comment on column ace_source.status is '数据源状态';
comment on column ace_source.metadata is '扩展元数据';
comment on column ace_source.created_at is '创建时间';
comment on column ace_source.updated_at is '更新时间';

create index idx_ace_source_scope on ace_source (workspace_key, project_key);
create index idx_ace_source_repo_branch on ace_source (repo_name, branch_name);
create index idx_ace_source_status on ace_source (status);

create table ace_document (
  id bigint generated always as identity primary key,
  source_id bigint not null default 0 references ace_source(id) on delete cascade,
  workspace_key text not null default '',
  project_key text not null default '',
  repo_name text not null default '',
  branch_name text not null default '',
  external_id text not null default '',
  title text not null default '',
  path text not null default '',
  content_type text not null default '',
  language text not null default '',
  checksum text not null default '',
  version text not null default '',
  status text not null default 'pending',
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (source_id, path),
  check (jsonb_typeof(metadata) = 'object'),
  check (status in ('pending', 'active', 'stale', 'deleted', 'indexing', 'failed')),
  check (content_type in ('code', 'markdown', 'pdf', 'html', 'text'))
);

comment on table ace_document is '索引文档表';
comment on column ace_document.id is '主键';
comment on column ace_document.source_id is '所属数据源主键';
comment on column ace_document.workspace_key is '工作空间标识';
comment on column ace_document.project_key is '项目标识';
comment on column ace_document.repo_name is '仓库名称';
comment on column ace_document.branch_name is '分支名称';
comment on column ace_document.external_id is '外部系统文档标识';
comment on column ace_document.title is '文档标题';
comment on column ace_document.path is '文档路径';
comment on column ace_document.content_type is '内容类型';
comment on column ace_document.language is '语言标识';
comment on column ace_document.checksum is '内容校验和';
comment on column ace_document.version is '文档版本';
comment on column ace_document.status is '文档状态';
comment on column ace_document.metadata is '扩展元数据';
comment on column ace_document.created_at is '创建时间';
comment on column ace_document.updated_at is '更新时间';

create index idx_ace_document_source_id on ace_document (source_id);
create index idx_ace_document_scope on ace_document (workspace_key, project_key);
create index idx_ace_document_repo_branch on ace_document (repo_name, branch_name);
create index idx_ace_document_content_type on ace_document (content_type);
create index idx_ace_document_status on ace_document (status);
create index idx_ace_document_checksum on ace_document (checksum);

create table ace_chunk (
  id bigint generated always as identity primary key,
  document_id bigint not null default 0 references ace_document(id) on delete cascade,
  workspace_key text not null default '',
  project_key text not null default '',
  repo_name text not null default '',
  branch_name text not null default '',
  chunk_index integer not null default 0,
  chunk_type text not null default '',
  content_type text not null default '',
  content text not null default '',
  token_count integer not null default 0,
  start_offset integer not null default 0,
  end_offset integer not null default 0,
  heading_path text not null default '',
  symbol_name text not null default '',
  symbol_kind text not null default '',
  metadata jsonb not null default '{}'::jsonb,
  embedding_model text not null default '',
  embedding_version text not null default '',
  qdrant_point_id text not null default '',
  created_at timestamptz not null default now(),
  unique (document_id, chunk_index),
  unique (qdrant_point_id),
  check (jsonb_typeof(metadata) = 'object'),
  check (chunk_index >= 0),
  check (token_count >= 0),
  check (start_offset >= 0),
  check (end_offset >= start_offset),
  check (chunk_type in ('symbol', 'paragraph', 'section', 'snippet')),
  check (content_type in ('code', 'markdown', 'pdf', 'html', 'text'))
);

comment on table ace_chunk is '索引切片表';
comment on column ace_chunk.id is '主键';
comment on column ace_chunk.document_id is '所属文档主键';
comment on column ace_chunk.workspace_key is '工作空间标识';
comment on column ace_chunk.project_key is '项目标识';
comment on column ace_chunk.repo_name is '仓库名称';
comment on column ace_chunk.branch_name is '分支名称';
comment on column ace_chunk.chunk_index is '切片序号';
comment on column ace_chunk.chunk_type is '切片类型';
comment on column ace_chunk.content_type is '内容类型';
comment on column ace_chunk.content is '切片内容';
comment on column ace_chunk.token_count is '切片 token 数量';
comment on column ace_chunk.start_offset is '起始偏移';
comment on column ace_chunk.end_offset is '结束偏移';
comment on column ace_chunk.heading_path is '文档标题层级路径';
comment on column ace_chunk.symbol_name is '代码符号名称';
comment on column ace_chunk.symbol_kind is '代码符号类型';
comment on column ace_chunk.metadata is '扩展元数据';
comment on column ace_chunk.embedding_model is '向量模型名称';
comment on column ace_chunk.embedding_version is '向量版本';
comment on column ace_chunk.qdrant_point_id is 'qdrant 点位标识';
comment on column ace_chunk.created_at is '创建时间';

create index idx_ace_chunk_document_id on ace_chunk (document_id);
create index idx_ace_chunk_scope on ace_chunk (workspace_key, project_key);
create index idx_ace_chunk_repo_branch on ace_chunk (repo_name, branch_name);
create index idx_ace_chunk_content_type_chunk_type on ace_chunk (content_type, chunk_type);
create index idx_ace_chunk_symbol_name on ace_chunk (symbol_name);
create index idx_ace_chunk_embedding_model on ace_chunk (embedding_model);

create table ace_indexing_job (
  id bigint generated always as identity primary key,
  source_id bigint not null default 0 references ace_source(id) on delete cascade,
  workspace_key text not null default '',
  project_key text not null default '',
  repo_name text not null default '',
  branch_name text not null default '',
  job_type text not null default '',
  status text not null default 'pending',
  error_message text not null default '',
  payload jsonb not null default '{}'::jsonb,
  started_at timestamptz not null default now(),
  finished_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  check (jsonb_typeof(payload) = 'object'),
  check (job_type in ('full', 'incremental', 'delete', 'reembed')),
  check (status in ('pending', 'running', 'success', 'failed', 'cancelled')),
  check (finished_at >= started_at)
);

comment on table ace_indexing_job is '索引任务表';
comment on column ace_indexing_job.id is '主键';
comment on column ace_indexing_job.source_id is '所属数据源主键';
comment on column ace_indexing_job.workspace_key is '工作空间标识';
comment on column ace_indexing_job.project_key is '项目标识';
comment on column ace_indexing_job.repo_name is '仓库名称';
comment on column ace_indexing_job.branch_name is '分支名称';
comment on column ace_indexing_job.job_type is '任务类型';
comment on column ace_indexing_job.status is '任务状态';
comment on column ace_indexing_job.error_message is '错误信息';
comment on column ace_indexing_job.payload is '任务载荷';
comment on column ace_indexing_job.started_at is '开始时间';
comment on column ace_indexing_job.finished_at is '结束时间';
comment on column ace_indexing_job.created_at is '创建时间';

create index idx_ace_indexing_job_source_id on ace_indexing_job (source_id);
create index idx_ace_indexing_job_scope on ace_indexing_job (workspace_key, project_key);
create index idx_ace_indexing_job_status_created_at on ace_indexing_job (status, created_at);

create table ace_search_log (
  id bigint generated always as identity primary key,
  workspace_key text not null default '',
  project_key text not null default '',
  repo_name text not null default '',
  branch_name text not null default '',
  query_text text not null default '',
  search_scope text not null default 'project',
  result_count integer not null default 0,
  latency_ms integer not null default 0,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  check (jsonb_typeof(metadata) = 'object'),
  check (search_scope in ('project', 'repo', 'branch', 'global')),
  check (result_count >= 0),
  check (latency_ms >= 0)
);

comment on table ace_search_log is '检索日志表';
comment on column ace_search_log.id is '主键';
comment on column ace_search_log.workspace_key is '工作空间标识';
comment on column ace_search_log.project_key is '项目标识';
comment on column ace_search_log.repo_name is '仓库名称';
comment on column ace_search_log.branch_name is '分支名称';
comment on column ace_search_log.query_text is '检索原始文本';
comment on column ace_search_log.search_scope is '检索范围';
comment on column ace_search_log.result_count is '结果数量';
comment on column ace_search_log.latency_ms is '检索耗时毫秒';
comment on column ace_search_log.metadata is '检索扩展元数据';
comment on column ace_search_log.created_at is '创建时间';

create index idx_ace_search_log_scope on ace_search_log (workspace_key, project_key);
create index idx_ace_search_log_created_at on ace_search_log (created_at);
