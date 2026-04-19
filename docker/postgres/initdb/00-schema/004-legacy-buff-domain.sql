create table if not exists public.buff_account (
  id bigint generated always as identity primary key,
  user_id bigint not null default 0,
  account_name varchar not null default '',
  buff_cookie text not null default '',
  role varchar not null default 'SCAN',
  status varchar not null default 'NORMAL',
  weight integer not null default 1,
  balance numeric(10,2) not null default 0.00,
  fail_count integer not null default 0,
  last_check_time timestamp not null default current_timestamp,
  user_agent varchar not null default '',
  remark varchar not null default '',
  warning_msg varchar not null default '',
  is_deleted smallint not null default 0,
  create_time timestamp not null default current_timestamp,
  update_time timestamp not null default current_timestamp,
  today_scan_count integer default 0,
  trade_success_count integer default 0,
  trade_total_count integer default 0,
  pending_balance numeric(10,2) default 0.00,
  platform varchar not null default 'BUFF',
  api_config text
);

comment on table public.buff_account is 'BUFF 账号配置表';
comment on column public.buff_account.id is '主键id';
comment on column public.buff_account.user_id is '系统用户id';
comment on column public.buff_account.account_name is '账号备注名';
comment on column public.buff_account.buff_cookie is 'BUFF 登录凭证';
comment on column public.buff_account.role is '账号角色：SCAN-扫描，TRADE-下单，BOTH-全能';
comment on column public.buff_account.status is '账号状态：NORMAL-正常，BANNED-封禁，MARKET_RESTRICTED-市场访问限制，TRADE_RESTRICTED-下单限制，INVALID-失效';
comment on column public.buff_account.weight is '调度权重';
comment on column public.buff_account.balance is '账号余额';
comment on column public.buff_account.fail_count is '连续请求失败计数';
comment on column public.buff_account.last_check_time is '最后有效性检测时间';
comment on column public.buff_account.user_agent is '关联的浏览器指纹';
comment on column public.buff_account.remark is '用户自定义备注信息';
comment on column public.buff_account.warning_msg is '异常说明';
comment on column public.buff_account.is_deleted is '逻辑删除标记';
comment on column public.buff_account.create_time is '创建时间';
comment on column public.buff_account.update_time is '更新时间';
comment on column public.buff_account.today_scan_count is '今日扫货总数';
comment on column public.buff_account.trade_success_count is '下单成功总数';
comment on column public.buff_account.trade_total_count is '下单总数';
comment on column public.buff_account.pending_balance is '待结算余额';
comment on column public.buff_account.platform is '所属平台：BUFF/C5';
comment on column public.buff_account.api_config is 'API 配置信息(JSON文本)';

create index if not exists idx_buff_account_user_id on public.buff_account (user_id);
create index if not exists idx_buff_account_role on public.buff_account (role);
create index if not exists idx_buff_account_status on public.buff_account (status);
create unique index if not exists uk_idx_single_sniper_account
  on public.buff_account (user_id)
  where role in ('TRADE', 'BOTH') and is_deleted = 0;

create table if not exists public.buff_goods_category (
  id bigint generated always as identity primary key,
  name varchar not null default '',
  parent_id bigint not null default 0,
  internal_name varchar not null default '',
  full_internal_name varchar not null default '',
  create_time timestamp not null default current_timestamp,
  update_time timestamp not null default current_timestamp,
  category_type varchar default 'type'
);

comment on table public.buff_goods_category is 'CSGO 饰品分类表';
comment on column public.buff_goods_category.id is '主键';
comment on column public.buff_goods_category.name is '分类名称';
comment on column public.buff_goods_category.parent_id is '父级分类id，0 表示一级分类';
comment on column public.buff_goods_category.internal_name is '内部标识';
comment on column public.buff_goods_category.full_internal_name is '完整内部标识';
comment on column public.buff_goods_category.create_time is '创建时间';
comment on column public.buff_goods_category.update_time is '更新时间';
comment on column public.buff_goods_category.category_type is '参数类型：type 对应 category 参数，weapon 对应 weapon 参数';

create unique index if not exists uk_buff_goods_category_internal_name on public.buff_goods_category (internal_name);
create index if not exists idx_buff_goods_category_parent_id on public.buff_goods_category (parent_id);
create index if not exists idx_buff_goods_category_name on public.buff_goods_category (name);
create index if not exists idx_buff_goods_category_type on public.buff_goods_category (category_type);
create index if not exists idx_buff_goods_category_full_internal_name on public.buff_goods_category (full_internal_name);

create table if not exists public.buff_goods (
  id bigint generated always as identity primary key,
  goods_id bigint not null default 0,
  name varchar not null default '',
  short_name varchar not null default '',
  internal_name varchar not null default '',
  category_id bigint not null default 0,
  rarity varchar not null default '',
  exterior varchar not null default '',
  market_hash_name varchar not null default '',
  icon_url text not null default '',
  original_icon_url text not null default '',
  create_time timestamp not null default current_timestamp,
  update_time timestamp not null default current_timestamp,
  tags json default '{}'::json,
  last_sync_tag varchar default '',
  constraint uk_buff_goods_goods_id unique (goods_id)
);

comment on table public.buff_goods is 'CSGO 商品表（遗留兼容表）';
comment on column public.buff_goods.id is '主键';
comment on column public.buff_goods.goods_id is '对应 Buff 的 goods_id';
comment on column public.buff_goods.name is '商品全名';
comment on column public.buff_goods.short_name is '简称';
comment on column public.buff_goods.internal_name is '内部标识';
comment on column public.buff_goods.category_id is '分类id';
comment on column public.buff_goods.rarity is '稀有度';
comment on column public.buff_goods.exterior is '外观/大磨损分类';
comment on column public.buff_goods.market_hash_name is 'Steam 市场 hash 名称';
comment on column public.buff_goods.icon_url is '图标URL';
comment on column public.buff_goods.original_icon_url is '原始图标URL';
comment on column public.buff_goods.create_time is '创建时间';
comment on column public.buff_goods.update_time is '更新时间';
comment on column public.buff_goods.tags is '标签信息，以 JSON 格式存储';
comment on column public.buff_goods.last_sync_tag is '最后同步版本标识';

create index if not exists idx_buff_goods_category_id on public.buff_goods (category_id);
create index if not exists idx_buff_goods_market_hash_name on public.buff_goods (market_hash_name);
create index if not exists idx_buff_goods_short_name_exterior on public.buff_goods (short_name, exterior);
create index if not exists idx_buff_goods_update_time on public.buff_goods (update_time);
create index if not exists idx_buff_goods_last_sync_tag on public.buff_goods (last_sync_tag);

create table if not exists public.buff_goods_stats (
  id bigint generated always as identity primary key,
  goods_id bigint not null default 0,
  avg_price_7d numeric(10,2) not null default 0.00,
  avg_price_24h numeric(10,2) not null default 0.00,
  buy_max_price numeric(10,2) not null default 0.00,
  sell_num integer not null default 0,
  liquidity_score integer not null default 0,
  update_time timestamp not null default current_timestamp
);

comment on table public.buff_goods_stats is '饰品市场行情统计雷达表';
comment on column public.buff_goods_stats.id is '主键ID';
comment on column public.buff_goods_stats.goods_id is 'BUFF 商品唯一标识ID';
comment on column public.buff_goods_stats.avg_price_7d is '过去7天成交均价';
comment on column public.buff_goods_stats.avg_price_24h is '过去24小时成交均价';
comment on column public.buff_goods_stats.buy_max_price is '当前最高求购价格';
comment on column public.buff_goods_stats.sell_num is '当前在售数量';
comment on column public.buff_goods_stats.liquidity_score is '流动性评分(0-100，分数越高变现越快)';
comment on column public.buff_goods_stats.update_time is '最后统计时间';

create unique index if not exists uk_stats_goods_id on public.buff_goods_stats (goods_id);
create index if not exists idx_stats_liquidity on public.buff_goods_stats (liquidity_score);

create table if not exists public.buff_price_history (
  id bigint generated always as identity primary key,
  goods_id bigint not null,
  price numeric(10,2),
  buy_max_price numeric(10,2),
  sell_num integer,
  record_time timestamp not null default current_timestamp,
  create_time timestamp not null default current_timestamp
);

comment on table public.buff_price_history is '商品价格历史表';
comment on column public.buff_price_history.id is '主键';
comment on column public.buff_price_history.goods_id is '关联商品id';
comment on column public.buff_price_history.price is '当前最低出售价';
comment on column public.buff_price_history.buy_max_price is '当前最高求购价';
comment on column public.buff_price_history.sell_num is '在售数量';
comment on column public.buff_price_history.record_time is '记录时间';
comment on column public.buff_price_history.create_time is '创建时间';

create index if not exists idx_buff_price_history_goods_id on public.buff_price_history (goods_id);
create index if not exists idx_buff_price_history_record_time on public.buff_price_history (record_time);
create unique index if not exists idx_price_history_dedup on public.buff_price_history (goods_id, create_time);

create table if not exists public.buff_leak_alert (
  id bigint generated always as identity primary key,
  task_id bigint not null default 0,
  goods_id bigint not null default 0,
  sell_id varchar not null default '',
  price numeric(10,2) not null default 0.00,
  expected_profit numeric(10,2) not null default 0.00,
  reason text not null default '',
  is_bought smallint not null default 0,
  create_time timestamp not null default current_timestamp
);

comment on table public.buff_leak_alert is '捡漏预警触发日志表';
comment on column public.buff_leak_alert.id is '主键ID';
comment on column public.buff_leak_alert.task_id is '触发该预警的扫描任务ID';
comment on column public.buff_leak_alert.goods_id is '商品ID';
comment on column public.buff_leak_alert.sell_id is 'BUFF 平台该笔上架单唯一标识';
comment on column public.buff_leak_alert.price is '触发预警时的挂牌价格';
comment on column public.buff_leak_alert.expected_profit is '预估理论利润额';
comment on column public.buff_leak_alert.reason is '触发逻辑简述';
comment on column public.buff_leak_alert.is_bought is '是否已购买(0未购，1已购)';
comment on column public.buff_leak_alert.create_time is '触发预警时间';

create index if not exists idx_alert_task_id on public.buff_leak_alert (task_id);
create index if not exists idx_alert_goods_id on public.buff_leak_alert (goods_id);
create index if not exists idx_alert_create_time on public.buff_leak_alert (create_time);

create table if not exists public.buff_sticker (
  id bigint generated always as identity primary key,
  sticker_id bigint not null default 0,
  name varchar not null default '',
  image_url text not null default '',
  price numeric(10,2) not null default 0.00,
  create_time timestamp not null default current_timestamp,
  update_time timestamp not null default current_timestamp,
  sell_num integer default 0
);

comment on table public.buff_sticker is 'BUFF 印花元数据及价值表';
comment on column public.buff_sticker.id is '主键ID';
comment on column public.buff_sticker.sticker_id is 'BUFF 平台印花唯一标识ID';
comment on column public.buff_sticker.name is '印花名称';
comment on column public.buff_sticker.image_url is '印花图片预览链接';
comment on column public.buff_sticker.price is '印花本体市场底价';
comment on column public.buff_sticker.create_time is '创建时间';
comment on column public.buff_sticker.update_time is '最后更新时间';
comment on column public.buff_sticker.sell_num is '在售数量';

create unique index if not exists uk_sticker_id on public.buff_sticker (sticker_id);
create index if not exists idx_sticker_name on public.buff_sticker (name);
