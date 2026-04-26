-- ============================================================
-- C5 库存管理数据库结构（PostgreSQL · 追加式 migration）
-- 日期: 2026-04-27
-- 目标:
-- 1. 为 c5_sniping_account 补充 steam_id，作为 C5 库存接口路径参数
-- 2. 新增 c5_inventory_item 最新库存快照表，按 account_id + asset_id 保持账号维度资产唯一
-- 3. 补齐库存查询、名称检索与同步时间查询索引
-- 幂等策略:
-- - 新字段使用 add column if not exists
-- - 新表使用 create table if not exists
-- - 索引使用 create index if not exists / create unique index if not exists
-- - 检查约束通过 pg_constraint 判重后追加
-- 回滚思路:
-- - 如需回滚，可新增 migration 软弃用 c5_inventory_item，并让业务停止读写库存快照
-- - c5_sniping_account.steam_id 可保留为空串，业务回退到不调用库存同步入口
-- ============================================================

begin;

alter table public.c5_sniping_account
    add column if not exists steam_id varchar(64) not null default '';

comment on column public.c5_sniping_account.steam_id is 'Steam ID，作为C5库存接口路径参数';

create table if not exists public.c5_inventory_item (
    id bigint generated always as identity primary key,
    user_id bigint not null default 0,
    account_id bigint not null default 0,
    steam_id varchar(64) not null default '',
    app_id integer not null default 730,
    asset_id varchar(64) not null default '',
    inventory_status varchar(32) not null default 'IN_STOCK',
    last_sync_time timestamp not null default now(),
    create_time timestamp not null default now(),
    update_time timestamp not null default now(),
    token varchar(256) not null default '',
    style_token varchar(256) not null default '',
    c5_status integer not null default 0,
    tradable_time varchar(64) not null default '',
    class_id varchar(64) not null default '',
    instance_id varchar(64) not null default '',
    inspect text not null default '',
    item_id varchar(64) not null default '',
    name varchar(255) not null default '',
    short_name varchar(255) not null default '',
    market_hash_name varchar(255) not null default '',
    image_url text not null default '',
    price numeric(12, 2) not null default 0,
    if_tradable boolean not null default false,
    wear numeric(12, 8) not null default 0,
    paint_index integer not null default 0,
    paint_seed integer not null default 0,
    inspect_image_url text not null default '',
    rarity varchar(64) not null default '',
    rarity_name varchar(128) not null default '',
    rarity_color varchar(32) not null default '',
    exterior varchar(64) not null default '',
    exterior_name varchar(128) not null default '',
    exterior_color varchar(32) not null default '',
    asset_info_json jsonb not null default '{}'::jsonb,
    item_info_json jsonb not null default '{}'::jsonb
);

comment on table public.c5_inventory_item is 'C5库存物品最新快照表';
comment on column public.c5_inventory_item.id is '主键ID';
comment on column public.c5_inventory_item.user_id is '系统用户ID';
comment on column public.c5_inventory_item.account_id is 'C5扫货账号ID';
comment on column public.c5_inventory_item.steam_id is 'Steam ID，库存接口路径参数';
comment on column public.c5_inventory_item.app_id is 'Steam应用ID，CS2默认为730';
comment on column public.c5_inventory_item.asset_id is 'Steam资产ID';
comment on column public.c5_inventory_item.inventory_status is '库存状态，IN_STOCK在库，REMOVED已移除';
comment on column public.c5_inventory_item.last_sync_time is '最后同步时间';
comment on column public.c5_inventory_item.create_time is '创建时间';
comment on column public.c5_inventory_item.update_time is '更新时间';
comment on column public.c5_inventory_item.token is 'C5物品token';
comment on column public.c5_inventory_item.style_token is 'C5样式token';
comment on column public.c5_inventory_item.c5_status is 'C5物品状态码';
comment on column public.c5_inventory_item.tradable_time is '可交易时间文本';
comment on column public.c5_inventory_item.class_id is 'Steam classId';
comment on column public.c5_inventory_item.instance_id is 'Steam instanceId';
comment on column public.c5_inventory_item.inspect is '检视链接';
comment on column public.c5_inventory_item.item_id is 'C5商品物品ID';
comment on column public.c5_inventory_item.name is '物品名称';
comment on column public.c5_inventory_item.short_name is '物品短名称';
comment on column public.c5_inventory_item.market_hash_name is 'Steam市场Hash名称';
comment on column public.c5_inventory_item.image_url is '物品图片地址';
comment on column public.c5_inventory_item.price is 'C5展示价格';
comment on column public.c5_inventory_item.if_tradable is '是否可交易';
comment on column public.c5_inventory_item.wear is '磨损值';
comment on column public.c5_inventory_item.paint_index is '图案编号';
comment on column public.c5_inventory_item.paint_seed is '图案模板';
comment on column public.c5_inventory_item.inspect_image_url is '检视图片地址';
comment on column public.c5_inventory_item.rarity is '稀有度代码';
comment on column public.c5_inventory_item.rarity_name is '稀有度名称';
comment on column public.c5_inventory_item.rarity_color is '稀有度颜色';
comment on column public.c5_inventory_item.exterior is '外观代码';
comment on column public.c5_inventory_item.exterior_name is '外观名称';
comment on column public.c5_inventory_item.exterior_color is '外观颜色';
comment on column public.c5_inventory_item.asset_info_json is 'C5 assetInfo原始JSON';
comment on column public.c5_inventory_item.item_info_json is 'C5 itemInfo原始JSON';

create unique index if not exists uk_c5_inventory_item_account_asset
    on public.c5_inventory_item (account_id, asset_id);

create index if not exists idx_c5_inventory_item_user_account_status
    on public.c5_inventory_item (user_id, account_id, inventory_status);

create index if not exists idx_c5_inventory_item_user_name
    on public.c5_inventory_item (user_id, market_hash_name);

create index if not exists idx_c5_inventory_item_sync_time
    on public.c5_inventory_item (account_id, last_sync_time desc);

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'chk_c5_inventory_item_inventory_status'
          and conrelid = 'public.c5_inventory_item'::regclass
    ) then
        alter table public.c5_inventory_item
            add constraint chk_c5_inventory_item_inventory_status check (inventory_status in ('IN_STOCK', 'REMOVED'));
    end if;

    if not exists (
        select 1
        from pg_constraint
        where conname = 'chk_c5_inventory_item_price'
          and conrelid = 'public.c5_inventory_item'::regclass
    ) then
        alter table public.c5_inventory_item
            add constraint chk_c5_inventory_item_price check (price >= 0);
    end if;

    if not exists (
        select 1
        from pg_constraint
        where conname = 'chk_c5_inventory_item_app_id'
          and conrelid = 'public.c5_inventory_item'::regclass
    ) then
        alter table public.c5_inventory_item
            add constraint chk_c5_inventory_item_app_id check (app_id > 0);
    end if;
end $$;

-- 复核 SQL：
-- select count(*) from public.c5_inventory_item;
-- select account_id, asset_id, inventory_status, last_sync_time from public.c5_inventory_item limit 10;

commit;
