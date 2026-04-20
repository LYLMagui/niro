create table if not exists public.buff_scan_task (
  id bigint generated always as identity primary key,
  name varchar not null default '',
  user_id bigint not null default 0,
  cs2_goods_id bigint,
  max_price numeric(10,2),
  min_paintwear numeric(10,6) not null default 0.000000,
  max_paintwear numeric(10,6) not null default 1.000000,
  buy_count integer not null default 1,
  status integer not null default 0,
  create_time timestamp not null default current_timestamp,
  update_time timestamp not null default current_timestamp,
  cron_expression varchar,
  duration_minutes integer default 0,
  scan_interval integer default 5,
  task_type integer default 0,
  min_profit numeric(10,2) default 0.00,
  scan_interval_min integer,
  scan_interval_max integer,
  rest_period integer default 0,
  run_mode varchar not null default 'BOTH',
  target_trade_account_id bigint,
  target_task_id bigint default 0,
  platform varchar not null default 'BUFF',
  extra_config text,
  safety_margin numeric(10,2),
  ladder_step numeric(10,2),
  last_error text,
  finish_time timestamp
);

comment on table public.buff_scan_task is '扫货任务配置表';
comment on column public.buff_scan_task.id is '主键';
comment on column public.buff_scan_task.name is '任务名称(通常是商品名)';
comment on column public.buff_scan_task.user_id is '创建用户ID';
comment on column public.buff_scan_task.cs2_goods_id is '任务目标商品ID，对应cs2_goods表id';
comment on column public.buff_scan_task.max_price is '目标最高价格(包含)';
comment on column public.buff_scan_task.min_paintwear is '最小磨损(包含)';
comment on column public.buff_scan_task.max_paintwear is '最大磨损(包含)';
comment on column public.buff_scan_task.buy_count is '计划购买数量';
comment on column public.buff_scan_task.status is '任务状态：0-停止，1-运行中，2-已完成，3-异常，4-系统任务，5-定时等待中';
comment on column public.buff_scan_task.create_time is '创建时间';
comment on column public.buff_scan_task.update_time is '更新时间';
comment on column public.buff_scan_task.cron_expression is 'Cron 触发表达式(空则立即开始)';
comment on column public.buff_scan_task.duration_minutes is '单次运行时长(分钟)/工作周期';
comment on column public.buff_scan_task.scan_interval is '运行期间的扫描间隔(秒)';
comment on column public.buff_scan_task.task_type is '任务类型：0-炼金扫货，1-站内倒卖，2-分类同步，3-商品全量同步';
comment on column public.buff_scan_task.min_profit is '站内倒卖任务的最小预期利润';
comment on column public.buff_scan_task.scan_interval_min is '最小扫描间隔(秒)';
comment on column public.buff_scan_task.scan_interval_max is '最大扫描间隔(秒)';
comment on column public.buff_scan_task.rest_period is '暂停时长(分钟)';
comment on column public.buff_scan_task.run_mode is '运行模式：SCAN-仅扫描，TRADE-仅交易，BOTH-全能模式';
comment on column public.buff_scan_task.target_trade_account_id is '关联的下单账号ID(仅 SCAN 模式使用)';
comment on column public.buff_scan_task.target_task_id is '关联的下单任务ID(仅 scan/both 模式使用)';
comment on column public.buff_scan_task.platform is '所属平台：BUFF/C5';
comment on column public.buff_scan_task.extra_config is '平台特殊配置(JSON文本)';
comment on column public.buff_scan_task.safety_margin is '安全边际(用于价格锚定)';
comment on column public.buff_scan_task.ladder_step is '锚定阶梯(价格调整步长)';
comment on column public.buff_scan_task.last_error is '最近一次任务运行失败的错误简述';
comment on column public.buff_scan_task.finish_time is '任务完成时间';

create index if not exists idx_scan_task_user on public.buff_scan_task (user_id);
create index if not exists idx_scan_task_status on public.buff_scan_task (status);
create index if not exists idx_buff_scan_task_cs2_goods_id on public.buff_scan_task (cs2_goods_id);
create index if not exists idx_buff_scan_task_run_mode on public.buff_scan_task (run_mode);
create index if not exists idx_buff_scan_task_target_task_id on public.buff_scan_task (target_task_id);

create table if not exists public.buff_scan_task_account (
  id bigint generated always as identity primary key,
  task_id bigint not null,
  account_id bigint not null,
  create_time timestamp default current_timestamp,
  constraint uk_task_account unique (task_id, account_id)
);

comment on table public.buff_scan_task_account is '任务与账号关联表';
comment on column public.buff_scan_task_account.id is '主键ID';
comment on column public.buff_scan_task_account.task_id is '任务ID';
comment on column public.buff_scan_task_account.account_id is '账号ID';
comment on column public.buff_scan_task_account.create_time is '创建时间';

create index if not exists idx_task_account_task_id on public.buff_scan_task_account (task_id);
create index if not exists idx_task_account_account_id on public.buff_scan_task_account (account_id);

create table if not exists public.trade_order_record (
  id bigint generated always as identity primary key,
  user_id bigint not null default 0,
  task_id bigint not null default 0,
  account_id bigint not null default 0,
  platform varchar not null default 'BUFF',
  goods_name varchar not null default '',
  market_hash_name varchar not null default '',
  goods_img varchar not null default '',
  order_id varchar not null default '',
  price numeric(10,2) not null default 0.00,
  status smallint not null default 0,
  error_msg varchar not null default '',
  extra_info jsonb not null default '{}'::jsonb,
  create_time timestamp not null default current_timestamp,
  update_time timestamp not null default current_timestamp,
  out_trade_no varchar,
  is_deleted smallint not null default 0
);

comment on table public.trade_order_record is '多平台交易订单记录表';
comment on column public.trade_order_record.id is '主键ID';
comment on column public.trade_order_record.user_id is '用户ID';
comment on column public.trade_order_record.task_id is '关联任务ID';
comment on column public.trade_order_record.account_id is '使用的账号ID';
comment on column public.trade_order_record.platform is '平台标识：BUFF/C5';
comment on column public.trade_order_record.goods_name is '商品显示名称';
comment on column public.trade_order_record.market_hash_name is 'Steam 市场 hash 名称';
comment on column public.trade_order_record.goods_img is '商品图片';
comment on column public.trade_order_record.order_id is '平台侧订单号';
comment on column public.trade_order_record.price is '下单价格';
comment on column public.trade_order_record.status is '状态：0-处理中，1-成功，2-失败，3-取消';
comment on column public.trade_order_record.error_msg is '失败原因';
comment on column public.trade_order_record.extra_info is '扩展信息(JSONB)';
comment on column public.trade_order_record.create_time is '创建时间';
comment on column public.trade_order_record.update_time is '更新时间';
comment on column public.trade_order_record.out_trade_no is '系统内部请求流水号';
comment on column public.trade_order_record.is_deleted is '是否删除：0-否，1-是';

create index if not exists idx_trade_order_user on public.trade_order_record (user_id);
create index if not exists idx_trade_order_task on public.trade_order_record (task_id);
create index if not exists idx_trade_order_platform on public.trade_order_record (platform);
create index if not exists idx_trade_order_time on public.trade_order_record (create_time);
create index if not exists idx_trade_order_out_trade_no on public.trade_order_record (out_trade_no);
create index if not exists idx_order_id on public.trade_order_record (order_id);

create table if not exists public.user_platform_setting (
  id bigint generated always as identity primary key,
  user_id bigint not null default 0,
  payment_method varchar default 'BALANCE',
  create_time timestamp default current_timestamp,
  update_time timestamp default current_timestamp,
  wecom_corpid varchar,
  wecom_corpsecret varchar,
  wecom_agentid varchar,
  wecom_touser varchar,
  email_enabled boolean not null default false,
  email_host varchar not null default '',
  email_port integer not null default 465,
  email_account varchar not null default '',
  email_password varchar not null default '',
  email_receiver varchar not null default '',
  c5_app_key varchar not null default '',
  c5_secret_key varchar not null default '',
  c5_trade_url varchar not null default '',
  steam_trade_url varchar not null default ''
);

comment on table public.user_platform_setting is '用户多平台配置表';
comment on column public.user_platform_setting.id is '主键ID';
comment on column public.user_platform_setting.user_id is '用户ID';
comment on column public.user_platform_setting.payment_method is '支付方式：BALANCE-余额，ALIPAY-支付宝，WECHAT-微信';
comment on column public.user_platform_setting.create_time is '创建时间';
comment on column public.user_platform_setting.update_time is '更新时间';
comment on column public.user_platform_setting.wecom_corpid is '企业微信企业ID';
comment on column public.user_platform_setting.wecom_corpsecret is '企业微信应用Secret';
comment on column public.user_platform_setting.wecom_agentid is '企业微信应用AgentID';
comment on column public.user_platform_setting.wecom_touser is '企业微信接收人';
comment on column public.user_platform_setting.email_enabled is '是否开启邮件通知';
comment on column public.user_platform_setting.email_host is 'SMTP服务器地址';
comment on column public.user_platform_setting.email_port is 'SMTP端口';
comment on column public.user_platform_setting.email_account is '发件人邮箱账号';
comment on column public.user_platform_setting.email_password is '发件人邮箱授权码/密码';
comment on column public.user_platform_setting.email_receiver is '收件人邮箱';
comment on column public.user_platform_setting.c5_app_key is 'C5 平台 API Key';
comment on column public.user_platform_setting.c5_secret_key is 'C5 平台 Secret Key';
comment on column public.user_platform_setting.c5_trade_url is 'C5 平台交易链接';
comment on column public.user_platform_setting.steam_trade_url is 'Steam 交易链接';

create index if not exists idx_user_platform_setting_user_id on public.user_platform_setting (user_id);

create table if not exists public.sys_invite_code (
  id bigint generated always as identity primary key,
  code varchar not null default '',
  issuer_user_id bigint not null default 0,
  max_use_count integer not null default 1,
  used_count integer not null default 0,
  status smallint not null default 1,
  expire_time timestamp not null default '9999-12-31 23:59:59',
  remark varchar not null default '',
  created_at timestamp not null default now(),
  updated_at timestamp not null default now(),
  constraint uk_sys_invite_code_code unique (code),
  constraint chk_sys_invite_code_status check (status in (0, 1)),
  constraint chk_sys_invite_code_max_use_count check (max_use_count >= 1),
  constraint chk_sys_invite_code_used_count check (used_count >= 0 and used_count <= max_use_count)
);

comment on table public.sys_invite_code is '系统邀请码表';
comment on column public.sys_invite_code.id is '主键';
comment on column public.sys_invite_code.code is '邀请码';
comment on column public.sys_invite_code.issuer_user_id is '签发人用户ID，0 表示系统签发';
comment on column public.sys_invite_code.max_use_count is '允许使用的最大次数';
comment on column public.sys_invite_code.used_count is '已使用次数';
comment on column public.sys_invite_code.status is '状态：1 启用，0 停用';
comment on column public.sys_invite_code.expire_time is '过期时间，9999-12-31 23:59:59 表示永不过期';
comment on column public.sys_invite_code.remark is '备注';
comment on column public.sys_invite_code.created_at is '创建时间';
comment on column public.sys_invite_code.updated_at is '更新时间';
