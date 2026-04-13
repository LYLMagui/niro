# PostgreSQL SQL Standard

本文档记录 PostgreSQL 在“数据库设计 + 数据库变更 + 迁移脚本”场景下的推荐语法与工作流。

它不是泛泛而谈的 PostgreSQL 介绍，而是用于指导你在实际任务里怎么安全地建表、改表、补注释、补索引和输出迁移 SQL。

## 1. Scope

适用于以下场景：

- `create table`
- `alter table`
- 新增字段、修改字段、删除字段
- 新增索引、唯一约束、检查约束、外键
- 编写 PostgreSQL 迁移脚本
- 补齐表注释、字段注释

## 2. Syntax baseline

- SQL 关键字统一使用小写
- 表名、列名、索引名统一使用 `snake_case`
- 默认不要给标识符加双引号
- 优先输出可直接执行的标准 PostgreSQL 语句，不输出混合方言 SQL

## 3. Comment rules

这是 PostgreSQL 下的硬规则。

### 3.1 Table comments
PostgreSQL 的表注释统一使用 `comment on table ... is '...'`，不要写 MySQL 风格的内联 `comment '...'`。

推荐在需要时带上 schema：

```sql
comment on table public.trade_order is '交易订单表';
```

### 3.2 Column comments
字段注释统一使用 `comment on column schema.table.column is '...'`：

```sql
comment on column public.trade_order.id is '主键';
comment on column public.trade_order.user_id is '用户主键';
comment on column public.trade_order.status is '订单状态';
```

如果当前 SQL 明确依赖默认 schema，也可以省略 schema，但不能混成 MySQL 的列内注释语法。

### 3.3 Add-column changes must include comments
给已有表新增字段时，不能只写 `alter table ... add column ...`，必须同时补注释：

```sql
alter table public.trade_order add column remark text not null default '';
comment on column public.trade_order.remark is '备注';
```

### 3.4 Table comment + column comment template
推荐模板：

```sql
create table trade_order (
  id bigint generated always as identity primary key,
  user_id bigint not null,
  status text not null default 'pending',
  created_at timestamptz not null default now()
);

comment on table trade_order is '交易订单表';
comment on column trade_order.id is '主键';
comment on column trade_order.user_id is '用户主键';
comment on column trade_order.status is '订单状态';
comment on column trade_order.created_at is '创建时间';
```

## 4. Data type rules

优先遵循以下 PostgreSQL 选型：

- 主键：`bigint generated always as identity`
- 时间：`timestamptz`
- 金额：`numeric(p,s)`
- 可变结构数据：`jsonb`
- 长文本：`text`
- 布尔：`boolean not null`

不要使用：

- `timestamp`（无时区）
- `money`
- `serial`
- 混合大小写列名

## 5. Create-table workflow

### 5.1 New table checklist
新建表时至少同时考虑：

- 主键
- 必要的 `not null`
- 合理的默认值
- 唯一约束 / 检查约束
- 外键是否需要
- 外键索引是否要手动补
- 表注释
- 字段注释

### 5.2 Example: create table with comments and indexes
```sql
create table trade_order (
  id bigint generated always as identity primary key,
  user_id bigint not null,
  order_no text not null,
  status text not null default 'pending',
  total_amount numeric(18,2) not null,
  ext_attrs jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (order_no),
  check (total_amount >= 0),
  check (jsonb_typeof(ext_attrs) = 'object')
);

comment on table trade_order is '交易订单表';
comment on column trade_order.id is '主键';
comment on column trade_order.user_id is '用户主键';
comment on column trade_order.order_no is '订单号';
comment on column trade_order.status is '订单状态';
comment on column trade_order.total_amount is '订单金额';
comment on column trade_order.ext_attrs is '扩展属性';
comment on column trade_order.created_at is '创建时间';
comment on column trade_order.updated_at is '更新时间';

create index idx_trade_order_user_id on trade_order (user_id);
create index idx_trade_order_status on trade_order (status);
create index idx_trade_order_created_at on trade_order (created_at);
```

## 6. Alter-table workflow

### 6.1 Add nullable column first when risk is uncertain
如果是大表，且你不确定直接加 `not null default ...` 的代价，优先分步：

1. 先加可空列或仅加默认值
2. 回填历史数据
3. 再补 `not null`
4. 最后补注释

### 6.2 Safe add-column example
```sql
alter table trade_order add column buyer_note text;
comment on column trade_order.buyer_note is '买家备注';

update trade_order
set buyer_note = ''
where buyer_note is null;

alter table trade_order alter column buyer_note set default '';
alter table trade_order alter column buyer_note set not null;
```

### 6.3 Change column type
修改字段类型时优先显式 `using`，避免依赖隐式转换：

```sql
alter table trade_order
alter column total_amount type numeric(18,2)
using total_amount::numeric(18,2);
```

### 6.4 Change default / not null
```sql
alter table trade_order alter column status set default 'pending';
alter table trade_order alter column status set not null;
alter table trade_order alter column status drop default;
alter table trade_order alter column status drop not null;
```

## 7. Constraint and index rules

### 7.1 Foreign keys need manual indexes
PostgreSQL 不会自动为外键列创建索引。只要加了外键，通常就要补索引：

```sql
alter table trade_order
add constraint fk_trade_order_user_id
foreign key (user_id) references sys_user(id);

create index idx_trade_order_user_id on trade_order (user_id);
```

### 7.2 Unique and check constraints
```sql
alter table trade_order
add constraint uk_trade_order_order_no unique (order_no);

alter table trade_order
add constraint chk_trade_order_status
check (status in ('pending', 'paid', 'canceled'));
```

### 7.3 Concurrent index creation
如果是在高写入环境给大表补索引，优先评估是否使用：

```sql
create index concurrently idx_trade_order_paid_at on trade_order (paid_at);
```

注意：`create index concurrently` 不能放进事务块里执行。

## 8. Migration risk rules

以下情况必须显式提示风险：

- 给大表新增 `not null` 列
- 给大表新增易变默认值，如 `now()`、`gen_random_uuid()`
- 大表改字段类型
- 大表补唯一约束或重建索引
- 线上直接执行可能阻塞写入的 `alter table`

优先思路：

- 能分步就分步
- 能先回填就先回填
- 能并发建索引就评估 `concurrently`
- 能避免整表重写就避免整表重写

## 9. PostgreSQL-specific reminders

- 外键不会自动建索引
- `unique` 默认允许多个 `null`
- `jsonb` 优先于 `json`
- 时间字段优先 `timestamptz`
- 主键优先 `generated always as identity`
- SQL 关键字统一小写

## 10. Example migration pattern

```sql
alter table trade_order add column paid_at timestamptz;
comment on column trade_order.paid_at is '支付时间';

update trade_order
set paid_at = created_at
where status = 'paid'
  and paid_at is null;

create index concurrently idx_trade_order_paid_at on trade_order (paid_at);
```

## 11. Reference sources

- `D:\MySpace\niro\.claude\skills\postgresql-table-design\SKILL.md`
- `D:\MySpace\niro\niro-ace\sql\schema.sql`
