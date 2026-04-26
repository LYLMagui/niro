# PostgreSQL SQL Standard

本文档记录 PostgreSQL 在“数据库设计 + 数据库变更 + 迁移脚本”场景下的推荐语法与工作流。

它不是泛泛而谈的 PostgreSQL 介绍，而是用于指导你在实际任务里怎么安全地建表、改表、补注释、补索引和输出迁移 SQL。

## 1. Scope

适用于以下场景：

- `create table`
- `alter table`
- 新增字段、修改字段、删除字段
- 新增索引、唯一约束、检查约束
- 编写 PostgreSQL 迁移脚本
- 补齐表注释、字段注释

## 2. Syntax baseline

- SQL 关键字统一使用小写
- 表名、列名、索引名统一使用 `snake_case`
- 默认不要给标识符加双引号
- 优先输出可直接执行的标准 PostgreSQL 语句，不输出混合方言 SQL
- 禁止使用外键；表关系通过关联字段、唯一约束、检查约束和必要索引表达
- 字段默认必须带默认值；只有 PostgreSQL 官方明确说明该类型或场景不允许默认值时，才允许例外，并且要在回答中说明理由
- 通用时间类字段统一使用无时区类型，例如 `timestamp not null default now()`

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
当前项目 PostgreSQL 建表基线明确采用：字符串默认空串、金额/折扣字段默认 `0` 或 `0.00`、通用时间字段使用 `timestamp not null default now()`。

推荐模板：

```sql
create table trade_order (
  id bigint generated always as identity primary key,
  user_id bigint not null default 0,
  status varchar(20) not null default '',
  note text not null default '',
  created_at timestamp not null default now(),
  updated_at timestamp not null default now()
);

comment on table trade_order is '交易订单表';
comment on column trade_order.id is '主键';
comment on column trade_order.user_id is '用户主键';
comment on column trade_order.status is '订单状态';
comment on column trade_order.note is '备注';
comment on column trade_order.created_at is '创建时间';
comment on column trade_order.updated_at is '更新时间';
```

## 4. Data type rules

优先遵循以下 PostgreSQL 选型，但如果当前项目已有明确 SQL 基线，优先服从项目现状：

- 主键：`bigint generated always as identity`
- 通用创建/更新时间：`timestamp not null default now()`
- 日期：`date not null default current_date`
- 金额：`numeric(p,s)`，并补默认值
- 文本备注：`text not null default ''`
- 短字符串：`varchar(n) not null default ''`
- 布尔：`boolean not null default false`
- 可变结构数据：确实需要时再使用 `jsonb`，并优先补合法默认值

当前项目已存在的 PostgreSQL 风格基线包括：

- 日期字段可用：`date not null default current_date`
- 折扣字段可用：`numeric(4,2) not null default 0.00`
- 金额字段可用：`numeric(10,2) not null default 0`
- 记录顺序字段可用：`integer not null default 0`

硬规则：

- 所有字段都应显式设置默认值
- 若某字段不设置默认值，必须是 PostgreSQL 官方明确限制该类型或该场景不允许默认值，并在输出中说明理由
- 通用时间字段必须保持无时区，不要改成 `timestamptz`

不要使用：

- `money`
- `serial`
- 混合大小写列名
- 外键

## 5. Create-table workflow

### 5.1 New table checklist
新建表时至少同时考虑：

- 主键
- 必要的 `not null`
- 每个字段是否都已补默认值
- 唯一约束 / 检查约束
- 关联字段索引是否需要手动补
- 表注释
- 字段注释

### 5.2 Example: create table with comments and indexes
```sql
create table trade_order (
  id bigint generated always as identity primary key,
  user_id bigint not null default 0,
  order_no varchar(64) not null default '',
  order_date date not null default current_date,
  status varchar(20) not null default '',
  total_amount numeric(10,2) not null default 0,
  discount numeric(4,2) not null default 0.00,
  note text not null default '',
  created_at timestamp not null default now(),
  updated_at timestamp not null default now(),
  constraint uk_trade_order_order_no unique (order_no),
  constraint chk_trade_order_total_amount check (total_amount >= 0),
  constraint chk_trade_order_discount check (discount >= 0 and discount <= 1)
);

comment on table trade_order is '交易订单表';
comment on column trade_order.id is '主键';
comment on column trade_order.user_id is '用户主键';
comment on column trade_order.order_no is '订单号';
comment on column trade_order.order_date is '订单日期';
comment on column trade_order.status is '订单状态';
comment on column trade_order.total_amount is '订单金额';
comment on column trade_order.discount is '折扣，取值范围0到1';
comment on column trade_order.note is '备注';
comment on column trade_order.created_at is '创建时间';
comment on column trade_order.updated_at is '更新时间';

create index idx_trade_order_user_id_order_date on trade_order (user_id, order_date desc);
create index idx_trade_order_status on trade_order (status);
```

## 6. Alter-table workflow

### 6.1 Add nullable column first when risk is uncertain
如果是大表，且你不确定直接加 `not null default ...` 的代价，优先分步，但最终仍应满足“字段要有默认值”的规则：

1. 先加字段，并尽早补默认值
2. 回填历史数据
3. 再补 `not null`
4. 最后补注释

### 6.2 Safe add-column example
```sql
alter table trade_order add column buyer_note text default '';
comment on column trade_order.buyer_note is '买家备注';

update trade_order
set buyer_note = ''
where buyer_note is null;

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
alter table trade_order alter column status set default '';
alter table trade_order alter column status set not null;
```

## 7. Constraint and index rules

### 7.1 Do not use foreign keys
本项目 PostgreSQL 规范里禁止使用外键。

表达表关系时，使用以下组合：

- 关联字段本身，例如 `user_id`、`order_id`
- 必要的唯一约束
- 必要的检查约束
- 关联字段索引

```sql
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

- 禁止使用外键
- `unique` 默认允许多个 `null`
- `jsonb` 优先于 `json`
- 通用创建/更新时间字段优先沿用项目基线：`timestamp not null default now()`
- 除 PostgreSQL 官方明确限制的场景外，所有字段都应显式设置默认值
- 主键优先 `generated always as identity`
- SQL 关键字统一小写

## 10. Example migration pattern

```sql
alter table trade_order add column paid_at timestamp default '1970-01-01 00:00:00';
comment on column trade_order.paid_at is '支付时间';

update trade_order
set paid_at = created_at
where status = 'paid'
  and paid_at = '1970-01-01 00:00:00';

create index concurrently idx_trade_order_paid_at on trade_order (paid_at);
```

## 11. Reference sources

- `D:\MySpace\niro\.claude\skills\postgresql-table-design\SKILL.md`
- `D:\MySpace\niro\docker\postgres\reference\niro-ace-schema.sql`
