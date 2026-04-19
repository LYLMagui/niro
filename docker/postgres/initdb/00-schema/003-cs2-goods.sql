create table if not exists public.cs2_goods (
  id bigint generated always as identity primary key,
  market_hash_name varchar(255) not null default '',
  display_name varchar(255) not null default '',
  base_name varchar(255) not null default '',
  short_name varchar(255) not null default '',
  internal_name varchar(255) not null default '',
  item_type varchar(64) not null default '',
  weapon_type varchar(64) not null default '',
  rarity varchar(64) not null default '',
  exterior_code integer not null default -1,
  exterior_name varchar(64) not null default '',
  has_exterior boolean not null default false,
  stattrak boolean not null default false,
  souvenir boolean not null default false,
  min_wear numeric(12,9) not null default 0,
  max_wear numeric(12,9) not null default 0,
  image_url text not null default '',
  original_image_url text not null default '',
  source_payload jsonb not null default '{}'::jsonb,
  enabled boolean not null default true,
  created_at timestamp not null default now(),
  updated_at timestamp not null default now(),
  constraint uk_cs2_goods_market_hash_name unique (market_hash_name),
  constraint chk_cs2_goods_exterior_code check (exterior_code between -1 and 4),
  constraint chk_cs2_goods_min_wear check (min_wear >= 0 and min_wear <= 1),
  constraint chk_cs2_goods_max_wear check (max_wear >= 0 and max_wear <= 1),
  constraint chk_cs2_goods_wear_range check (min_wear <= max_wear)
);

comment on table public.cs2_goods is 'CS2 商品主表，一行表示一个最终商品变体';
comment on column public.cs2_goods.id is '主键';
comment on column public.cs2_goods.market_hash_name is 'Steam 市场哈希名称，商品唯一锚点';
comment on column public.cs2_goods.display_name is '完整展示名称';
comment on column public.cs2_goods.base_name is '基础名称，不含外观后缀';
comment on column public.cs2_goods.short_name is '短名称，用于 OCR/搜索匹配';
comment on column public.cs2_goods.internal_name is '内部规范化名称';
comment on column public.cs2_goods.item_type is '商品类型，如 weapon_skin/case/sticker/key/glove/knife';
comment on column public.cs2_goods.weapon_type is '武器类型，如 rifle/pistol/smg，无则为空';
comment on column public.cs2_goods.rarity is '稀有度';
comment on column public.cs2_goods.exterior_code is '外观编码：-1 无外观，0 崭新出厂，1 略有磨损，2 久经沙场，3 破损不堪，4 战痕累累';
comment on column public.cs2_goods.exterior_name is '外观名称';
comment on column public.cs2_goods.has_exterior is '是否存在外观维度';
comment on column public.cs2_goods.stattrak is '是否为 StatTrak';
comment on column public.cs2_goods.souvenir is '是否为纪念品';
comment on column public.cs2_goods.min_wear is '最小磨损值';
comment on column public.cs2_goods.max_wear is '最大磨损值';
comment on column public.cs2_goods.image_url is '展示图地址';
comment on column public.cs2_goods.original_image_url is '原始图地址';
comment on column public.cs2_goods.source_payload is '来源原始扩展 JSON 数据';
comment on column public.cs2_goods.enabled is '是否启用';
comment on column public.cs2_goods.created_at is '创建时间';
comment on column public.cs2_goods.updated_at is '更新时间';

create index if not exists idx_cs2_goods_base_name_exterior_code
  on public.cs2_goods (base_name, exterior_code);

create index if not exists idx_cs2_goods_short_name_exterior_code
  on public.cs2_goods (short_name, exterior_code);

create index if not exists idx_cs2_goods_item_type
  on public.cs2_goods (item_type);

create index if not exists idx_cs2_goods_weapon_type
  on public.cs2_goods (weapon_type);

create index if not exists idx_cs2_goods_rarity
  on public.cs2_goods (rarity);

create index if not exists idx_cs2_goods_enabled_updated_at
  on public.cs2_goods (enabled, updated_at desc);

create index if not exists idx_cs2_goods_lower_market_hash_name
  on public.cs2_goods ((lower(market_hash_name)));

create table if not exists public.cs2_goods_source_map (
  id bigint generated always as identity primary key,
  goods_id bigint not null default 0,
  source_type varchar(32) not null default '',
  source_id varchar(128) not null default '',
  source_name varchar(255) not null default '',
  extra jsonb not null default '{}'::jsonb,
  created_at timestamp not null default now(),
  updated_at timestamp not null default now(),
  constraint uk_cs2_goods_source_map_source unique (source_type, source_id),
  constraint chk_cs2_goods_source_map_goods_id check (goods_id >= 0)
);

comment on table public.cs2_goods_source_map is 'CS2 商品来源映射表';
comment on column public.cs2_goods_source_map.id is '主键';
comment on column public.cs2_goods_source_map.goods_id is '关联 cs2_goods.id 的本地商品主键';
comment on column public.cs2_goods_source_map.source_type is '来源类型，如 bymykel/buff/c5/manual';
comment on column public.cs2_goods_source_map.source_id is '来源侧商品标识';
comment on column public.cs2_goods_source_map.source_name is '来源侧名称';
comment on column public.cs2_goods_source_map.extra is '来源扩展 JSON 数据';
comment on column public.cs2_goods_source_map.created_at is '创建时间';
comment on column public.cs2_goods_source_map.updated_at is '更新时间';

create index if not exists idx_cs2_goods_source_map_goods_id
  on public.cs2_goods_source_map (goods_id);

create index if not exists idx_cs2_goods_source_map_source_type
  on public.cs2_goods_source_map (source_type);
