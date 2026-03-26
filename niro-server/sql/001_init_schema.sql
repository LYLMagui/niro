-- Migration DDL generated from current PostgreSQL schema snapshot.
-- Review before applying to a different environment.

-- 初始化序列，确保 nextval 引用存在
create sequence if not exists public.buff_goods_id_seq;
create sequence if not exists public.buff_goods_categories_id_seq;
create sequence if not exists public.buff_goods_stats_id_seq;
create sequence if not exists public.buff_leak_alerts_id_seq;
create sequence if not exists public.buff_price_history_id_seq;
create sequence if not exists public.buff_scan_task_id_seq;
create sequence if not exists public.buff_scan_task_account_id_seq;
create sequence if not exists public.buff_stickers_id_seq;
create sequence if not exists public.sys_role_role_id_seq;
create sequence if not exists public.sys_user_id_seq;
create sequence if not exists public.trade_order_record_id_seq;
create sequence if not exists public.user_buff_settings_id_seq;

create table public.buff_account (
  id bigint generated always as identity not null,
  user_id bigint not null default 0,
  account_name varchar(64) not null default ''::varchar,
  buff_cookie text not null default ''::text,
  role varchar(20) not null default 'SCAN'::varchar,
  status varchar(20) not null default 'NORMAL'::varchar,
  weight integer not null default 1,
  balance numeric(10,2) not null default 0.00,
  fail_count integer not null default 0,
  last_check_time timestamp without time zone not null default current_timestamp,
  user_agent varchar(512) not null default ''::varchar,
  remark varchar(255) not null default ''::varchar,
  warning_msg varchar(512) not null default ''::varchar,
  is_deleted smallint not null default 0,
  create_time timestamp without time zone not null default current_timestamp,
  update_time timestamp without time zone not null default current_timestamp,
  today_scan_count integer default 0,
  trade_success_count integer default 0,
  trade_total_count integer default 0,
  pending_balance numeric(10,2) default 0.00,
  platform varchar(32) not null default 'BUFF'::varchar,
  api_config text,
  constraint buff_account_pkey primary key (id)
);

comment on table public.buff_account is 'BUFF 账号配置表';
comment on column public.buff_account.id is '主键id';
comment on column public.buff_account.user_id is '系统用户id';
comment on column public.buff_account.account_name is '账号备注名';
comment on column public.buff_account.buff_cookie is 'BUFF 登录凭证';
comment on column public.buff_account.role is '账号角色：SCAN-扫描, TRADE-下单, BOTH-全能';
comment on column public.buff_account.status is '账号状态：NORMAL-正常, BANNED-封禁, MARKET_RESTRICTED-市场访问限制, TRADE_RESTRICTED-下单限制, INVALID-失效';
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
comment on column public.buff_account.trade_total_count is '下单总数（用于计算成功率）';
comment on column public.buff_account.pending_balance is '待结算余额';
comment on column public.buff_account.platform is '所属平台: BUFF/C5';
comment on column public.buff_account.api_config is 'API配置信息(JSON)';
create index idx_buff_account_user_id on public.buff_account using btree (user_id);
create index idx_buff_account_role on public.buff_account using btree (role);
create index idx_buff_account_status on public.buff_account using btree (status);
create unique index uk_idx_single_sniper_account on public.buff_account using btree (user_id) where ((role)::text = any ((array['TRADE'::character varying, 'BOTH'::character varying])::text[])) and (is_deleted = 0);

create table public.buff_goods (
  id bigint not null default nextval('buff_goods_id_seq'::regclass),
  goods_id bigint not null default 0,
  name varchar(255) not null default ''::varchar,
  short_name varchar(255) not null default ''::varchar,
  internal_name varchar(255) not null default ''::varchar,
  category_id bigint not null default 0,
  rarity varchar(100) not null default ''::varchar,
  exterior varchar(100) not null default ''::varchar,
  market_hash_name varchar(255) not null default ''::varchar,
  icon_url text not null default ''::text,
  original_icon_url text not null default ''::text,
  create_time timestamp without time zone not null default current_timestamp,
  update_time timestamp without time zone not null default current_timestamp,
  tags json default '{}'::json,
  last_sync_tag varchar(50) default ''::varchar,
  constraint buff_goods_pkey primary key (id),
  constraint buff_goods_goods_id_key unique (goods_id)
);

comment on table public.buff_goods is 'csgo商品表';
comment on column public.buff_goods.id is '主键';
comment on column public.buff_goods.goods_id is '对应 Buff 的 goods_id';
comment on column public.buff_goods.name is '商品全名';
comment on column public.buff_goods.short_name is '简称';
comment on column public.buff_goods.internal_name is '内部标识';
comment on column public.buff_goods.category_id is '分类ID';
comment on column public.buff_goods.rarity is '稀有度';
comment on column public.buff_goods.exterior is '外观/大磨损分类';
comment on column public.buff_goods.market_hash_name is 'steam市场hash名称';
comment on column public.buff_goods.icon_url is '图标URL';
comment on column public.buff_goods.original_icon_url is '原始图标URL';
comment on column public.buff_goods.create_time is '创建时间';
comment on column public.buff_goods.update_time is '更新时间';
comment on column public.buff_goods.tags is '标签信息，以JSON格式存储';
comment on column public.buff_goods.last_sync_tag is '最后同步版本标识';
create index idx_buff_goods_category_id on public.buff_goods using btree (category_id);
create index idx_buff_goods_create_time on public.buff_goods using btree (create_time);
create index idx_buff_goods_hash_name on public.buff_goods using btree (market_hash_name);
create index idx_buff_goods_last_sync_tag on public.buff_goods using btree (last_sync_tag);
create index idx_buff_goods_market_hash_name on public.buff_goods using btree (market_hash_name);
create index idx_buff_goods_update_time on public.buff_goods using btree (update_time);
create index idx_goods_category_id on public.buff_goods using btree (category_id);
create unique index idx_goods_goods_id on public.buff_goods using btree (goods_id);
create index idx_goods_update_time on public.buff_goods using btree (update_time);

create table public.buff_goods_20260123 (
  id bigint,
  goods_id bigint,
  name varchar(255),
  short_name varchar(255),
  internal_name varchar(255),
  category_id bigint,
  rarity varchar(100),
  exterior varchar(100),
  market_hash_name varchar(255),
  icon_url text,
  original_icon_url text,
  create_time timestamp without time zone,
  update_time timestamp without time zone,
  tags json,
  last_sync_tag varchar(50)
);

create table public.buff_goods_category (
  id bigint not null default nextval('buff_goods_categories_id_seq'::regclass),
  name varchar(100) not null default ''::varchar,
  parent_id bigint not null default 0,
  internal_name varchar(100) not null default ''::varchar,
  full_internal_name varchar(100) not null default ''::varchar,
  create_time timestamp without time zone not null default current_timestamp,
  update_time timestamp without time zone not null default current_timestamp,
  category_type varchar(50) default 'type'::varchar,
  constraint buff_goods_categories_pkey primary key (id),
  constraint buff_goods_categories_internal_name_key unique (internal_name)
);

comment on table public.buff_goods_category is 'csgo饰品分类表';
comment on column public.buff_goods_category.id is '主键';
comment on column public.buff_goods_category.name is 'api传入标识';
comment on column public.buff_goods_category.full_internal_name is 'api传入标识-全称';
comment on column public.buff_goods_category.parent_id is '父级ID';
comment on column public.buff_goods_category.internal_name is '内部标识';
comment on column public.buff_goods_category.create_time is '创建时间';
comment on column public.buff_goods_category.update_time is '更新时间';
comment on column public.buff_goods_category.category_type is '参数类型: type(对应category参数), weapon(对应weapon参数)';
create index idx_categories_full_internal_name on public.buff_goods_category using btree (full_internal_name);
create index idx_categories_internal_name on public.buff_goods_category using btree (internal_name);
create index idx_categories_name on public.buff_goods_category using btree (name);
create index idx_categories_parent on public.buff_goods_category using btree (parent_id);
create unique index idx_category_internal_name on public.buff_goods_category using btree (internal_name);
create index idx_category_type on public.buff_goods_category using btree (category_type);

create table public.buff_goods_stats (
  id bigint not null default nextval('buff_goods_stats_id_seq'::regclass),
  goods_id bigint not null default 0,
  avg_price_7d numeric(10,2) not null default 0.00,
  avg_price_24h numeric(10,2) not null default 0.00,
  buy_max_price numeric(10,2) not null default 0.00,
  sell_num integer not null default 0,
  liquidity_score integer not null default 0,
  update_time timestamp without time zone not null default current_timestamp,
  constraint buff_goods_stats_pkey primary key (id),
  constraint uk_stats_goods_id unique (goods_id)
);

comment on table public.buff_goods_stats is '饰品市场行情统计雷达表';
comment on column public.buff_goods_stats.id is '主键ID';
comment on column public.buff_goods_stats.goods_id is 'BUFF商品唯一标识ID';
comment on column public.buff_goods_stats.avg_price_7d is '过去7天成交均价';
comment on column public.buff_goods_stats.avg_price_24h is '过去24小时成交均价';
comment on column public.buff_goods_stats.buy_max_price is '当前最高求购价格';
comment on column public.buff_goods_stats.sell_num is '当前在售数量';
comment on column public.buff_goods_stats.liquidity_score is '流动性评分(0-100，分数越高变现越快)';
comment on column public.buff_goods_stats.update_time is '最后统计时间';
create index idx_stats_liquidity on public.buff_goods_stats using btree (liquidity_score);

create table public.buff_leak_alert (
  id bigint not null default nextval('buff_leak_alerts_id_seq'::regclass),
  task_id bigint not null default 0,
  goods_id bigint not null default 0,
  sell_id varchar(100) not null default ''::varchar,
  price numeric(10,2) not null default 0.00,
  expected_profit numeric(10,2) not null default 0.00,
  reason text not null default ''::text,
  is_bought smallint not null default 0,
  create_time timestamp without time zone not null default current_timestamp,
  constraint buff_leak_alerts_pkey primary key (id)
);

comment on table public.buff_leak_alert is '捡漏预警触发日志表';
comment on column public.buff_leak_alert.id is '主键ID';
comment on column public.buff_leak_alert.task_id is '触发该预警的扫描任务ID';
comment on column public.buff_leak_alert.goods_id is '商品ID';
comment on column public.buff_leak_alert.sell_id is 'BUFF平台该笔上架单的唯一标识';
comment on column public.buff_leak_alert.price is '触发预警时的挂牌价格';
comment on column public.buff_leak_alert.expected_profit is '预估理论利润额';
comment on column public.buff_leak_alert.reason is '触发逻辑简述(如：低磨/印花溢价/超低价)';
comment on column public.buff_leak_alert.is_bought is '是否已购买(0:未购, 1:已购)';
comment on column public.buff_leak_alert.create_time is '触发预警的时间';
create index idx_alert_create_time on public.buff_leak_alert using btree (create_time);
create index idx_alert_goods_id on public.buff_leak_alert using btree (goods_id);
create index idx_alert_task_id on public.buff_leak_alert using btree (task_id);

create table public.buff_price_history (
  id bigint not null default nextval('buff_price_history_id_seq'::regclass),
  goods_id bigint not null default 0,
  record_time timestamp without time zone not null default current_timestamp,
  market_price numeric(10,2) not null default 0.00,
  highest_price numeric(10,2) not null default 0.00,
  lowest_price numeric(10,2) not null default 0.00,
  avg_price numeric(10,2) not null default 0.00,
  volume integer not null default 0,
  price_change numeric(10,2) not null default 0.00,
  create_time timestamp without time zone not null default current_timestamp,
  constraint buff_price_history_pkey primary key (id)
);

comment on table public.buff_price_history is '商品价格历史表';
comment on column public.buff_price_history.id is '主键';
comment on column public.buff_price_history.goods_id is '商品ID';
comment on column public.buff_price_history.record_time is '记录时间';
comment on column public.buff_price_history.market_price is '市场价';
comment on column public.buff_price_history.highest_price is '最高价';
comment on column public.buff_price_history.lowest_price is '最低价';
comment on column public.buff_price_history.avg_price is '均价';
comment on column public.buff_price_history.volume is '成交量';
comment on column public.buff_price_history.price_change is '涨跌幅';
comment on column public.buff_price_history.create_time is '创建时间';
create index idx_buff_price_history_goods_id on public.buff_price_history using btree (goods_id);
create index idx_buff_price_history_record_time on public.buff_price_history using btree (record_time);
create unique index idx_price_history_dedup on public.buff_price_history using btree (goods_id, create_time);

create table public.buff_scan_task (
  id bigint not null default nextval('buff_scan_task_id_seq'::regclass),
  name varchar(255) not null default ''::varchar,
  user_id bigint not null default 0,
  goods_id bigint,
  max_price numeric,
  min_paintwear numeric not null default 0.000000,
  max_paintwear numeric not null default 1.000000,
  buy_count integer not null default 1,
  status integer not null default 0,
  create_time timestamp without time zone not null default current_timestamp,
  update_time timestamp without time zone not null default current_timestamp,
  cron_expression varchar(255),
  duration_minutes integer default 0,
  scan_interval integer default 5,
  task_type integer default 0,
  min_profit numeric default 0.00,
  scan_interval_min integer,
  scan_interval_max integer,
  rest_period integer default 0,
  run_mode varchar(255) not null default 'BOTH'::varchar,
  target_trade_account_id bigint,
  target_task_id bigint default 0,
  platform varchar(32) not null default 'BUFF'::varchar,
  extra_config text,
  safety_margin numeric,
  ladder_step numeric,
  last_error text,
  finish_time timestamp without time zone
);

comment on table public.buff_scan_task is '扫货任务配置表';
comment on column public.buff_scan_task.id is '主键ID';
comment on column public.buff_scan_task.name is '任务名称';
comment on column public.buff_scan_task.user_id is '创建用户ID';
comment on column public.buff_scan_task.goods_id is '目标商品ID';
comment on column public.buff_scan_task.max_price is '最高价格限制';
comment on column public.buff_scan_task.min_paintwear is '最小磨损';
comment on column public.buff_scan_task.max_paintwear is '最大磨损';
comment on column public.buff_scan_task.buy_count is '购买数量';
comment on column public.buff_scan_task.status is '任务状态';
comment on column public.buff_scan_task.create_time is '创建时间';
comment on column public.buff_scan_task.update_time is '更新时间';
comment on column public.buff_scan_task.cron_expression is 'Cron表达式';
comment on column public.buff_scan_task.duration_minutes is '持续分钟数';
comment on column public.buff_scan_task.scan_interval is '扫描间隔';
comment on column public.buff_scan_task.task_type is '任务类型';
comment on column public.buff_scan_task.min_profit is '最低利润';
comment on column public.buff_scan_task.scan_interval_min is '最小扫描间隔';
comment on column public.buff_scan_task.scan_interval_max is '最大扫描间隔';
comment on column public.buff_scan_task.rest_period is '休眠时间';
comment on column public.buff_scan_task.run_mode is '运行模式';
comment on column public.buff_scan_task.target_trade_account_id is '目标下单账号';
comment on column public.buff_scan_task.target_task_id is '目标关联任务';
comment on column public.buff_scan_task.platform is '平台标识';
comment on column public.buff_scan_task.extra_config is '扩展配置';
comment on column public.buff_scan_task.safety_margin is '安全边际';
comment on column public.buff_scan_task.ladder_step is '阶梯步长';
comment on column public.buff_scan_task.last_error is '最后错误信息';
comment on column public.buff_scan_task.finish_time is '完成时间';
create index idx_buff_scan_task_run_mode on public.buff_scan_task using btree (run_mode);
create index idx_buff_scan_task_target_task_id on public.buff_scan_task using btree (target_task_id);
create index idx_scan_task_status on public.buff_scan_task using btree (status);
create index idx_scan_task_user on public.buff_scan_task using btree (user_id);

create table public.buff_scan_task_account (
  id bigint not null default nextval('buff_scan_task_account_id_seq'::regclass),
  task_id bigint not null,
  account_id bigint not null,
  create_time timestamp without time zone default current_timestamp,
  constraint buff_scan_task_account_pkey primary key (id),
  constraint uk_task_account unique (task_id, account_id)
);

comment on table public.buff_scan_task_account is '任务与账号关联表';
comment on column public.buff_scan_task_account.id is '主键ID';
comment on column public.buff_scan_task_account.task_id is '任务ID';
comment on column public.buff_scan_task_account.account_id is '账号ID';
comment on column public.buff_scan_task_account.create_time is '创建时间';
create index idx_task_account_account_id on public.buff_scan_task_account using btree (account_id);
create index idx_task_account_task_id on public.buff_scan_task_account using btree (task_id);

create table public.buff_sticker (
  id bigint not null default nextval('buff_stickers_id_seq'::regclass),
  sticker_id bigint not null default 0,
  name varchar(255) not null default ''::varchar,
  image_url text not null default ''::text,
  price numeric(10,2) not null default 0.00,
  create_time timestamp without time zone not null default current_timestamp,
  update_time timestamp without time zone not null default current_timestamp,
  sell_num integer default 0,
  constraint buff_stickers_pkey primary key (id),
  constraint uk_sticker_id unique (sticker_id)
);

comment on table public.buff_sticker is 'BUFF印花元数据及价值表';
comment on column public.buff_sticker.id is '主键ID';
comment on column public.buff_sticker.sticker_id is 'BUFF平台印花唯一标识ID';
comment on column public.buff_sticker.name is '印花中文名称';
comment on column public.buff_sticker.image_url is '印花图片预览链接';
comment on column public.buff_sticker.price is '印花本体市场底价(用于计算溢价)';
comment on column public.buff_sticker.create_time is '创建时间';
comment on column public.buff_sticker.update_time is '最后更新时间';
comment on column public.buff_sticker.sell_num is '在售数量';
create index idx_sticker_name on public.buff_sticker using btree (name);

create table public.sys_menu (
  id bigint generated always as identity not null,
  parent_id bigint not null default 0,
  title varchar(64) not null,
  name varchar(64),
  path varchar(255) not null,
  component varchar(255),
  icon varchar(64),
  sort_order integer default 0,
  type smallint default 1,
  permission varchar(100),
  hidden boolean default false,
  keep_alive boolean default true,
  redirect varchar(255),
  del_flag smallint default 0,
  create_time timestamptz default now(),
  update_time timestamptz default now(),
  status smallint not null default 1,
  constraint sys_menu_pkey primary key (id)
);

comment on table public.sys_menu is '系统菜单表';
comment on column public.sys_menu.id is '主键ID';
comment on column public.sys_menu.parent_id is '父菜单ID';
comment on column public.sys_menu.title is '菜单标题(显示名称)';
comment on column public.sys_menu.name is '路由名称(PascalCase, 用于KeepAlive)';
comment on column public.sys_menu.path is '路由路径(相对路径)';
comment on column public.sys_menu.component is '组件路径(views/xxx.vue 或 ParentView)';
comment on column public.sys_menu.sort_order is '排序(越小越靠前)';
comment on column public.sys_menu.type is '类型:0目录,1菜单,2按钮';
comment on column public.sys_menu.permission is '权限标识(user:add)';
comment on column public.sys_menu.hidden is '是否隐藏(0显示,1隐藏)';
comment on column public.sys_menu.keep_alive is '是否缓存';
comment on column public.sys_menu.redirect is '重定向地址(仅目录使用)';
comment on column public.sys_menu.del_flag is '逻辑删除(0正常,1删除)';
create index idx_sys_menu_parent_id on public.sys_menu using btree (parent_id);
create index idx_sys_menu_sort on public.sys_menu using btree (sort_order);
create index idx_sys_menu_status on public.sys_menu using btree (status);

create table public.sys_role (
  role_id bigint not null default nextval('sys_role_role_id_seq'::regclass),
  role_name varchar(30) not null,
  role_key varchar(100) not null,
  role_sort integer default 0,
  data_scope character(1) default '1'::bpchar,
  status smallint default 1,
  del_flag smallint default 0,
  create_by varchar(64) default ''::varchar,
  create_time timestamp without time zone,
  update_by varchar(64) default ''::varchar,
  update_time timestamp without time zone,
  remark varchar(500),
  constraint sys_role_pkey primary key (role_id)
);

comment on table public.sys_role is '角色信息表';
comment on column public.sys_role.role_id is '角色ID';
comment on column public.sys_role.role_name is '角色名称';
comment on column public.sys_role.role_key is '角色权限字符串';
comment on column public.sys_role.status is '角色状态（1正常 0停用）';
create index idx_sys_role_del_flag on public.sys_role using btree (del_flag);
create unique index uk_sys_role_role_key_active on public.sys_role using btree (role_key) where (del_flag = 0);

create table public.sys_role_menu (
  role_id bigint not null,
  menu_id bigint not null,
  constraint sys_role_menu_pkey primary key (role_id, menu_id)
);

comment on table public.sys_role_menu is '角色和菜单关联表';

create table public.sys_user (
  id bigint not null default nextval('sys_user_id_seq'::regclass),
  username varchar(50) not null,
  password varchar(100) not null default ''::varchar,
  nickname varchar(50) not null default ''::varchar,
  email varchar(100) not null default ''::varchar,
  avatar varchar(100) not null default ''::varchar,
  status smallint default 1,
  create_time timestamp without time zone not null default current_timestamp,
  update_time timestamp without time zone not null default current_timestamp,
  is_delete smallint not null default 0,
  constraint sys_user_pkey primary key (id),
  constraint sys_user_username_key unique (username)
);

comment on table public.sys_user is '系统用户表';
comment on column public.sys_user.id is '主键';
comment on column public.sys_user.username is '用户名';
comment on column public.sys_user.password is '密码';
comment on column public.sys_user.nickname is '昵称';
comment on column public.sys_user.email is '邮箱';
comment on column public.sys_user.avatar is '头像';
comment on column public.sys_user.status is '状态:1-正常,0-禁用';
comment on column public.sys_user.create_time is '创建时间';
comment on column public.sys_user.update_time is '更新时间';
comment on column public.sys_user.is_delete is '删除:0-否,1-是';

create table public.sys_user_role (
  user_id bigint not null,
  role_id bigint not null,
  constraint sys_user_role_pkey primary key (user_id, role_id)
);

comment on table public.sys_user_role is '用户和角色关联表';

create table public.trade_order_record (
  id bigint not null default nextval('trade_order_record_id_seq'::regclass),
  user_id bigint not null default 0,
  task_id bigint not null default 0,
  account_id bigint not null default 0,
  platform varchar(32) not null default 'BUFF'::varchar,
  goods_name varchar(255) not null default ''::varchar,
  market_hash_name varchar(255) not null default ''::varchar,
  goods_img varchar(512) not null default ''::varchar,
  order_id varchar(64) not null default ''::varchar,
  price numeric(10,2) not null default 0.00,
  status smallint not null default 0,
  error_msg varchar(512) not null default ''::varchar,
  extra_info jsonb not null default '{}'::jsonb,
  create_time timestamp without time zone not null default current_timestamp,
  update_time timestamp without time zone not null default current_timestamp,
  out_trade_no varchar(64),
  is_deleted smallint not null default 0,
  constraint trade_order_record_pkey primary key (id)
);

comment on table public.trade_order_record is '多平台交易订单记录表';
comment on column public.trade_order_record.platform is '平台标识: BUFF, C5';
comment on column public.trade_order_record.status is '状态: 0-处理中, 1-成功, 2-失败, 3-取消';
comment on column public.trade_order_record.extra_info is '扩展信息(JSONB)';
comment on column public.trade_order_record.out_trade_no is '系统内部请求流水号';
comment on column public.trade_order_record.is_deleted is '是否删除：0-否，1-是';
create index idx_order_id on public.trade_order_record using btree (order_id);
create index idx_trade_order_out_trade_no on public.trade_order_record using btree (out_trade_no);
create index idx_trade_order_platform on public.trade_order_record using btree (platform);
create index idx_trade_order_task on public.trade_order_record using btree (task_id);
create index idx_trade_order_time on public.trade_order_record using btree (create_time);
create index idx_trade_order_user on public.trade_order_record using btree (user_id);

create table public.user_platform_setting (
  id bigint not null default nextval('user_buff_settings_id_seq'::regclass),
  user_id bigint not null default 0,
  payment_method varchar(50) default 'BALANCE'::varchar,
  create_time timestamp without time zone default current_timestamp,
  update_time timestamp without time zone default current_timestamp,
  wecom_corpid varchar(100),
  wecom_corpsecret varchar(255),
  wecom_agentid varchar(100),
  wecom_touser varchar(100),
  email_enabled boolean not null default false,
  email_host varchar(100) not null default ''::varchar,
  email_port integer not null default 465,
  email_account varchar(100) not null default ''::varchar,
  email_password varchar(100) not null default ''::varchar,
  email_receiver varchar(100) not null default ''::varchar,
  c5_app_key varchar(255) not null default ''::varchar,
  c5_secret_key varchar(255) not null default ''::varchar,
  c5_trade_url varchar(500) not null default ''::varchar,
  steam_trade_url varchar(500) not null default ''::varchar,
  constraint user_buff_settings_pkey primary key (id)
);

comment on table public.user_platform_setting is '用户Buff配置表';
comment on column public.user_platform_setting.user_id is '用户ID';
comment on column public.user_platform_setting.payment_method is '支付方式: BALANCE-余额, ALIPAY-支付宝, WECHAT-微信';
comment on column public.user_platform_setting.wecom_corpid is '企业微信企业ID';
comment on column public.user_platform_setting.wecom_corpsecret is '企业微信应用Secret';
comment on column public.user_platform_setting.wecom_agentid is '企业微信应用AgentID';
comment on column public.user_platform_setting.wecom_touser is '企业微信接收人';
comment on column public.user_platform_setting.email_enabled is '是否开启邮件通知';
comment on column public.user_platform_setting.email_host is 'smtp服务器地址';
comment on column public.user_platform_setting.email_port is 'smtp端口';
comment on column public.user_platform_setting.email_account is '发件人邮箱账号';
comment on column public.user_platform_setting.email_password is '发件人邮箱授权码/密码';
comment on column public.user_platform_setting.email_receiver is '收件人邮箱';
comment on column public.user_platform_setting.c5_app_key is 'c5平台 api key';
comment on column public.user_platform_setting.c5_secret_key is 'c5平台 secret key';
comment on column public.user_platform_setting.c5_trade_url is 'c5平台交易链接';
comment on column public.user_platform_setting.steam_trade_url is 'steam交易链接';

-- seed data: rbac
insert into public.sys_role (
  role_id,
  role_name,
  role_key,
  role_sort,
  data_scope,
  status,
  del_flag,
  create_by,
  create_time,
  update_by,
  update_time,
  remark
)
values
  (1, '超级管理员', 'admin', 1, '1', 1, 0, 'admin', now(), 'admin', now(), '系统最高权限角色'),
  (2, '普通用户', 'user', 2, '2', 1, 0, 'admin', now(), 'admin', now(), '普通用户角色')
on conflict (role_key) where del_flag = 0 do nothing;

insert into public.sys_user (
  id,
  username,
  password,
  nickname,
  email,
  avatar,
  status,
  create_time,
  update_time,
  is_delete
)
values
  (1, 'admin', '$2a$10$BPg7L7jnPerfOc7KzE.ou.Ob.c84sXlDVvORTwB0SjMA4fJaM05He', '系统管理员', '', '', 1, now(), now(), 0),
  (2, 'user', '$2a$10$BPg7L7jnPerfOc7KzE.ou.Ob.c84sXlDVvORTwB0SjMA4fJaM05He', '普通用户', '', '', 1, now(), now(), 0)
on conflict (username) do nothing;

insert into public.sys_user_role (
  user_id,
  role_id
)
values
  (1, 1),
  (2, 2)
on conflict do nothing;

insert into public.sys_menu (
  id,
  parent_id,
  title,
  name,
  path,
  component,
  icon,
  sort_order,
  type,
  permission,
  hidden,
  keep_alive,
  redirect,
  del_flag,
  create_time,
  update_time,
  status
)
overriding system value
values
  (1, 0, '首页', 'Dashboard', 'dashboard', 'dashboard', 'home', 1, 1, 'system:dashboard:view', false, true, null, 0, now(), now(), 1),
  (2, 0, '扫货管理', 'Task', 'task', 'ParentView', 'server', 2, 0, null, false, true, '/task/manager/c5', 0, now(), now(), 1),
  (3, 2, '任务管理', 'TaskManager', 'manager', 'ParentView', 'app', 1, 0, null, false, true, '/task/manager/c5', 0, now(), now(), 1),
  (5, 3, 'C5平台', 'C5Task', 'c5', 'c5', 'server', 2, 1, 'task:c5:list', false, true, null, 0, now(), now(), 1),
  (6, 2, '订单记录', 'OrderRecord', 'record', 'record', 'history', 2, 1, 'task:record:list', false, true, null, 0, now(), now(), 1),
  (11, 2, '订单统计', 'OrderStatistics', 'inventory', 'inventory', 'dashboard', 3, 1, 'task:inventory:view', false, true, null, 0, now(), now(), 1),
  (12, 2, '利润统计', 'ProfitStatistics', 'profit', 'profit', 'chart-bar', 4, 1, 'system:profit:view', false, true, null, 0, now(), now(), 1),
  (14, 2, '开箱记录', 'UnboxRecord', 'unboxrecord', 'unboxrecord', 'gift', 5, 1, 'system:task:unboxrecord:view', false, true, null, 0, now(), now(), 1),
  (7, 0, '系统管理', 'System', 'system', 'ParentView', 'setting', 3, 0, null, false, true, '/system/goods', 0, now(), now(), 1),
  (8, 7, '商品管理', 'GoodsList', 'goods', 'goods', 'shop', 1, 1, 'system:goods:list', false, true, null, 0, now(), now(), 1),
  (13, 7, '印花管理', 'StickerList', 'sticker', 'sticker', 'file', 2, 1, 'system:sticker:list', false, true, null, 0, now(), now(), 1),
  (9, 7, '系统日志', 'SystemLogs', 'logs', 'logs', 'list', 3, 1, 'system:logs:list', false, true, null, 0, now(), now(), 1),
  (10, 7, '账号管理', 'AccountList', 'account', 'account', 'user', 4, 1, 'system:account:list', false, true, null, 0, now(), now(), 1),
  (15, 7, '权限管理', 'PermissionManage', 'permission', 'permission', 'secured', 5, 1, 'system:permission:manage', false, true, null, 0, now(), now(), 1),
  (16, 5, 'BUFF任务权限', 'BuffTaskPerm', 'buff-perm', null, 'server', 1, 2, 'task:buff:list', false, true, null, 0, now(), now(), 1),
  (17, 10, '新增/编辑账号', 'BuffAccountSave', 'account-save', null, 'edit', 1, 2, 'buff:account:save', false, true, null, 0, now(), now(), 1),
  (18, 10, '检测账号', 'BuffAccountCheck', 'account-check', null, 'check', 2, 2, 'buff:account:check', false, true, null, 0, now(), now(), 1),
  (19, 10, '批量检测账号', 'BuffAccountCheckAll', 'account-check-all', null, 'check-double', 3, 2, 'buff:account:check:all', false, true, null, 0, now(), now(), 1),
  (20, 10, '删除账号', 'BuffAccountDelete', 'account-delete', null, 'delete', 4, 2, 'buff:account:delete', false, true, null, 0, now(), now(), 1),
  (21, 10, '保存设置', 'SystemSettingsSave', 'settings-save', null, 'save', 5, 2, 'system:settings:save', false, true, null, 0, now(), now(), 1),
  (22, 10, '测试通知', 'SystemSettingsTestNotify', 'settings-test-notify', null, 'notification', 6, 2, 'system:settings:test-notify', false, true, null, 0, now(), now(), 1),
  (23, 13, '同步印花', 'StickerSync', 'sticker-sync', null, 'sync', 1, 2, 'system:sticker:sync', false, true, null, 0, now(), now(), 1),
  (24, 6, '修改订单', 'OrderRecordUpdate', 'record-update', null, 'edit', 1, 2, 'order:record:update', false, true, null, 0, now(), now(), 1),
  (25, 6, '删除订单', 'OrderRecordDelete', 'record-delete', null, 'delete', 2, 2, 'order:record:delete', false, true, null, 0, now(), now(), 1)
on conflict (id) do nothing;

insert into public.sys_role_menu (
  role_id,
  menu_id
)
values
  (1, 1),
  (1, 2),
  (1, 3),
  (1, 5),
  (1, 6),
  (1, 7),
  (1, 8),
  (1, 9),
  (1, 10),
  (1, 11),
  (1, 12),
  (1, 13),
  (1, 14),
  (1, 15),
  (1, 16),
  (1, 17),
  (1, 18),
  (1, 19),
  (1, 20),
  (1, 21),
  (1, 22),
  (1, 23),
  (1, 24),
  (1, 25),
  (2, 1),
  (2, 2),
  (2, 3),
  (2, 5),
  (2, 6),
  (2, 7),
  (2, 8),
  (2, 9),
  (2, 10),
  (2, 11),
  (2, 12),
  (2, 13),
  (2, 14),
  (2, 16),
  (2, 17),
  (2, 18),
  (2, 19),
  (2, 20),
  (2, 21),
  (2, 22),
  (2, 23),
  (2, 24),
  (2, 25)
on conflict do nothing;

select setval(pg_get_serial_sequence('public.sys_role', 'role_id'), coalesce((select max(role_id) from public.sys_role), 1), true);
select setval(pg_get_serial_sequence('public.sys_user', 'id'), coalesce((select max(id) from public.sys_user), 1), true);
select setval(pg_get_serial_sequence('public.sys_menu', 'id'), coalesce((select max(id) from public.sys_menu), 1), true);
