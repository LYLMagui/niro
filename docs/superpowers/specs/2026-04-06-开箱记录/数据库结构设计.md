# 开箱记录页数据库结构设计

日期：2026-04-14  
范围：`niro-client/src/views/UnboxRecord.vue` 对应的后端持久化模型与 PostgreSQL 建表方案

## 1. 目标

为开箱记录页设计一套可落地的 PostgreSQL 数据结构，覆盖：

1. 开箱记录头信息存储
2. 开箱记录明细存储
3. 用户维度的数据归属
4. 商品维度的逻辑关联
5. 后续接口按记录聚合统计的基础数据来源

本次目标不是设计统计表，也不是设计利润冗余字段，而是先把事实表建干净。

## 2. 已确认前提

### 2.1 数据库方言
当前项目使用 PostgreSQL。

识别依据：
- `niro-server/niro-web/src/main/resources/config/prod/application-prod.yml` 使用 `jdbc:postgresql://...`
- `niro-server/niro-web/src/main/resources/config/common/database.yml` 使用 `org.postgresql.Driver`
- `niro-server/niro-web/pom.xml` 引入 `org.postgresql:postgresql`

因此本设计和后续 SQL 均按 PostgreSQL 方言输出。

### 2.2 用户确认的边界
已确认以下规则：

1. 统计数据通过接口计算返回，不单独设计统计表。
2. 手续费、成本、到账、净利润、利润率、记录状态等自动计算字段不落库。
3. 不保留批次命名，统一改为“开箱记录 / record”。
4. 记录头表保留用户 id 和商品 id。
5. 商品名称字段使用 `box_name`，同时保留 `goods_id` 方便后续按商品 id 查询。
6. 记录表与明细表均禁止使用外键。
7. 明细表命名确定为 `unbox_record_item`。

## 3. 页面数据分析结论

当前页面包含两层数据：

### 3.1 记录头信息
对应一次开箱记录的基础信息：
- 开箱日期
- 箱子名称
- 默认折扣
- 备注
- 数据归属用户
- 关联商品 id

### 3.2 记录明细
对应一次开箱记录下的单条产出明细：
- 处理状态
- 箱子购入价
- 武器名称
- 游戏内售价
- 明细折扣
- 实际卖出价
- 明细备注
- 明细顺序

### 3.3 自动计算边界
以下内容不作为原始事实存储：
- 手续费
- 实际购入成本
- 到账
- 净利润
- 利润率
- 记录状态（未结算 / 部分结算 / 已结算）
- 顶部统计卡片结果
- 汇总表统计列

这些结果全部由接口基于事实表实时计算返回。

## 4. 最终数据模型

## 4.1 开箱记录表：`unbox_record`
职责：承载一次开箱记录的头信息，不存统计结果。

建议字段：
- `id`：主键
- `user_id`：用户 id
- `goods_id`：关联 `buff_goods.id` 的商品 id
- `unbox_date`：开箱日期
- `box_name`：箱子名称
- `default_discount`：默认折扣
- `note`：备注
- `created_at`：创建时间
- `updated_at`：更新时间

设计说明：
- 不保留 `batch_name`
- `goods_id` 为逻辑关联字段，不加数据库外键
- `user_id` 为数据归属字段，用于后续按用户查询与隔离
- 顶部汇总和记录状态不存表

## 4.2 开箱记录明细表：`unbox_record_item`
职责：承载一次开箱记录下的每条明细，是页面编辑器的事实来源。

建议字段：
- `id`：主键
- `record_id`：所属开箱记录 id
- `sort_no`：记录内顺序号
- `handling_status`：处理状态
- `box_purchase_price`：箱子购入价
- `weapon_name`：武器名称
- `in_game_price`：游戏内售价
- `discount`：明细折扣，允许覆盖记录默认折扣
- `actual_sell_price`：实际卖出价
- `note`：明细备注
- `created_at`：创建时间
- `updated_at`：更新时间

设计说明：
- `record_id` 为逻辑关联字段，不加数据库外键
- `sort_no` 用于稳定支持“下方新增 / 恢复默认顺序”
- `discount` 允许为空，表示继承 `unbox_record.default_discount`

## 4.3 逻辑关系
- `unbox_record 1 --- n unbox_record_item`
- 删除记录时，明细由服务层一并删除
- 数据完整性由 Service 层维护，不依赖数据库外键

## 5. PostgreSQL 字段类型方案

### 5.1 主键方案
两张表统一使用：

```sql
bigint generated always as identity primary key
```

### 5.2 `unbox_record` 字段类型
- `id` → `bigint`
- `user_id` → `bigint`
- `goods_id` → `bigint`
- `unbox_date` → `date`
- `box_name` → `varchar(100)`
- `default_discount` → `numeric(4,2)`
- `note` → `text`
- `created_at` → `timestamptz`
- `updated_at` → `timestamptz`

### 5.3 `unbox_record_item` 字段类型
- `id` → `bigint`
- `record_id` → `bigint`
- `sort_no` → `integer`
- `handling_status` → `varchar(20)`
- `box_purchase_price` → `numeric(10,2)`
- `weapon_name` → `varchar(200)`
- `in_game_price` → `numeric(10,2)`
- `discount` → `numeric(4,2)`，允许为空
- `actual_sell_price` → `numeric(10,2)`
- `note` → `text`
- `created_at` → `timestamptz`
- `updated_at` → `timestamptz`

## 6. 约束设计

## 6.1 `unbox_record` 约束
建议约束：
- `user_id not null`
- `goods_id not null`
- `unbox_date not null`
- `box_name not null`
- `default_discount not null default 0.72`
- `check (default_discount >= 0 and default_discount <= 1)`

## 6.2 `unbox_record_item` 约束
建议约束：
- `record_id not null`
- `sort_no not null`
- `handling_status not null`
- `box_purchase_price not null default 0`
- `in_game_price not null default 0`
- `actual_sell_price not null default 0`
- `check (handling_status in ('pending', 'discarded', 'stored', 'purchased'))`
- `check (discount is null or (discount >= 0 and discount <= 1))`
- `check (box_purchase_price >= 0)`
- `check (in_game_price >= 0)`
- `check (actual_sell_price >= 0)`
- `unique (record_id, sort_no)`

### 6.3 不做的约束
明确不做：
- 外键约束
- 级联删除约束
- 统计字段一致性约束
- 利润类派生字段约束

## 7. 索引策略

以当前页面查询模式为准，只保留必要索引：

### 7.1 `unbox_record`
- `idx_unbox_record_user_id_unbox_date` on `(user_id, unbox_date desc)`
- `idx_unbox_record_goods_id` on `(goods_id)`

用途：
- 按用户查询记录列表
- 按时间筛选本周 / 本月 / 本年 / 自定义日期
- 按商品 id 查询关联记录

### 7.2 `unbox_record_item`
- `idx_unbox_record_item_record_id_sort_no` on `(record_id, sort_no)`

用途：
- 查询某条记录下全部明细
- 保持明细展示顺序稳定

当前阶段不预建设性加入 `handling_status` 单列索引，避免过度设计。后续若确认存在高频状态筛选，再增量补充。

## 8. 注释规则

后续 SQL 必须完整包含：
- 表注释：`comment on table ...`
- 字段注释：`comment on column ...`

规则：
1. 两张表都必须有表注释。
2. 每个字段都必须有字段注释。
3. 不允许只给建表语句不给注释。
4. 全部按 PostgreSQL 注释语法输出，不混入 MySQL 风格列内注释。

## 9. SQL 文件组织方案

后续落地 SQL 时，使用：
- 目录：`docs/sql/`
- 文件名：`开箱记录表设计.sql`

文件内容包含：
1. `unbox_record` 建表语句
2. `unbox_record_item` 建表语句
3. check / unique 约束
4. 索引
5. 表注释
6. 字段注释

文件内容不包含：
- 统计视图
- 统计表
- 利润冗余字段
- 外键
- 触发器

## 10. 实现边界

本次数据库设计只解决：
- 开箱记录事实表建模
- PostgreSQL 建表语句输出
- 约束、索引、注释完整性

本次不解决：
- 统计接口实现
- Service / Controller / DTO / VO 代码
- 数据迁移脚本
- 历史数据导入
- 汇总结果缓存

## 11. 成功标准

本设计落地后应满足：
1. 能完整表达开箱记录页当前 UI 的头信息与明细数据。
2. 不依赖外键也能支撑后续服务层实现。
3. 所有统计字段都能基于事实表实时计算得出。
4. SQL 文件符合 PostgreSQL 方言，并带完整注释。
5. 数据结构保持最小闭环，不引入统计表或冗余利润字段。