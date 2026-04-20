create table if not exists public.unbox_record (
  id bigint generated always as identity primary key,
  user_id bigint not null default 0,
  box_goods_id bigint not null default 0,
  unbox_date date not null default current_date,
  box_name varchar(100) not null default '',
  default_discount numeric(4,2) not null default 0.00,
  note text not null default '',
  created_at timestamp not null default now(),
  updated_at timestamp not null default now(),
  constraint chk_unbox_record_default_discount
    check (default_discount >= 0 and default_discount <= 1)
);

create table if not exists public.unbox_record_item (
  id bigint generated always as identity primary key,
  record_id bigint not null default 0,
  sort_no integer not null default 0,
  handling_status varchar(20) not null default '',
  box_purchase_price numeric(10,2) not null default 0,
  weapon_name varchar(200) not null default '',
  in_game_price numeric(10,2) not null default 0,
  discount numeric(4,2) not null default 0.00,
  actual_sell_price numeric(10,2) not null default 0,
  note text not null default '',
  created_at timestamp not null default now(),
  updated_at timestamp not null default now(),
  wear numeric(17,16) not null default 0.0000000000000000,
  exterior integer not null default 0,
  constraint uk_unbox_record_item_record_id_sort_no unique (record_id, sort_no),
  constraint chk_unbox_record_item_handling_status
    check (handling_status in ('pending', 'discarded', 'stored', 'purchased')),
  constraint chk_unbox_record_item_discount
    check (discount >= 0 and discount <= 1),
  constraint chk_unbox_record_item_box_purchase_price
    check (box_purchase_price >= 0),
  constraint chk_unbox_record_item_in_game_price
    check (in_game_price >= 0),
  constraint chk_unbox_record_item_actual_sell_price
    check (actual_sell_price >= 0),
  constraint chk_unbox_record_item_wear
    check (wear >= 0 and wear <= 1),
  constraint chk_unbox_record_item_exterior
    check (exterior in (0, 1, 2, 3, 4))
);

create index if not exists idx_unbox_record_user_id_unbox_date
  on public.unbox_record (user_id, unbox_date desc);

create index if not exists idx_unbox_record_box_goods_id
  on public.unbox_record (box_goods_id);

create index if not exists idx_unbox_record_item_record_id_sort_no
  on public.unbox_record_item (record_id, sort_no);

comment on table public.unbox_record is '开箱记录表';
comment on column public.unbox_record.id is '主键';
comment on column public.unbox_record.user_id is '用户id';
comment on column public.unbox_record.box_goods_id is '箱子商品id，对应cs2_goods表id';
comment on column public.unbox_record.unbox_date is '开箱日期';
comment on column public.unbox_record.box_name is '箱子名称';
comment on column public.unbox_record.default_discount is '默认折扣，取值范围0到1';
comment on column public.unbox_record.note is '备注';
comment on column public.unbox_record.created_at is '创建时间';
comment on column public.unbox_record.updated_at is '更新时间';

comment on table public.unbox_record_item is '开箱记录明细表';
comment on column public.unbox_record_item.id is '主键';
comment on column public.unbox_record_item.record_id is '所属开箱记录id';
comment on column public.unbox_record_item.sort_no is '记录内顺序号';
comment on column public.unbox_record_item.handling_status is '处理状态：pending待处理、discarded丢弃、stored暂存、purchased已买';
comment on column public.unbox_record_item.box_purchase_price is '箱子购入价';
comment on column public.unbox_record_item.weapon_name is '武器名称';
comment on column public.unbox_record_item.in_game_price is '游戏内售价';
comment on column public.unbox_record_item.discount is '明细折扣，为空时继承记录默认折扣';
comment on column public.unbox_record_item.actual_sell_price is '实际卖出价';
comment on column public.unbox_record_item.note is '明细备注';
comment on column public.unbox_record_item.created_at is '创建时间';
comment on column public.unbox_record_item.updated_at is '更新时间';
comment on column public.unbox_record_item.wear is '磨损值，取值范围0到1，保留16位小数';
comment on column public.unbox_record_item.exterior is '外观：0崭新出厂、1略有磨损、2久经沙场、3破损不堪、4战痕累累';
