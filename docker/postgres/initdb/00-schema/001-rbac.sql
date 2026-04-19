create table if not exists public.sys_user (
  id bigint generated always as identity primary key,
  username varchar not null,
  password varchar not null default '',
  nickname varchar not null default '',
  email varchar not null default '',
  avatar varchar not null default '',
  status smallint default 1,
  create_time timestamp not null default current_timestamp,
  update_time timestamp not null default current_timestamp,
  is_delete smallint not null default 0
);

comment on table public.sys_user is '系统用户表';
comment on column public.sys_user.id is '主键ID';
comment on column public.sys_user.username is '用户名';
comment on column public.sys_user.password is '加密后的密码';
comment on column public.sys_user.nickname is '昵称';
comment on column public.sys_user.email is '邮箱';
comment on column public.sys_user.avatar is '头像URL';
comment on column public.sys_user.status is '状态: 1-正常, 0-禁用';
comment on column public.sys_user.create_time is '创建时间';
comment on column public.sys_user.update_time is '更新时间';
comment on column public.sys_user.is_delete is '是否删除: 0-否, 1-是';

create unique index if not exists sys_user_username_key on public.sys_user (username);

create table if not exists public.sys_role (
  role_id bigint generated always as identity primary key,
  role_name varchar not null,
  role_key varchar not null,
  role_sort integer default 0,
  data_scope char(1) default '1',
  status smallint default 1,
  del_flag smallint default 0,
  create_by varchar default '',
  create_time timestamp,
  update_by varchar default '',
  update_time timestamp,
  remark varchar default null
);

comment on table public.sys_role is '角色信息表';
comment on column public.sys_role.role_id is '角色ID';
comment on column public.sys_role.role_name is '角色名称';
comment on column public.sys_role.role_key is '角色权限字符串';
comment on column public.sys_role.role_sort is '显示顺序';
comment on column public.sys_role.data_scope is '数据范围';
comment on column public.sys_role.status is '角色状态（1正常 0停用）';
comment on column public.sys_role.del_flag is '删除标志（0存在 1删除）';
comment on column public.sys_role.create_by is '创建者';
comment on column public.sys_role.create_time is '创建时间';
comment on column public.sys_role.update_by is '更新者';
comment on column public.sys_role.update_time is '更新时间';
comment on column public.sys_role.remark is '备注';

create index if not exists idx_sys_role_del_flag on public.sys_role (del_flag);
create unique index if not exists uk_sys_role_role_key_active on public.sys_role (role_key) where del_flag = 0;

create table if not exists public.sys_menu (
  id bigint generated always as identity primary key,
  parent_id bigint not null default 0,
  title varchar not null,
  name varchar,
  path varchar not null,
  component varchar,
  icon varchar,
  sort_order integer default 0,
  type smallint default 1,
  permission varchar,
  hidden boolean default false,
  keep_alive boolean default true,
  redirect varchar,
  del_flag smallint default 0,
  create_time timestamptz default now(),
  update_time timestamptz default now(),
  status smallint not null default 1,
  constraint ck_sys_menu_status check (status in (0, 1))
);

comment on table public.sys_menu is '系统菜单表';
comment on column public.sys_menu.id is '菜单ID';
comment on column public.sys_menu.parent_id is '父菜单ID';
comment on column public.sys_menu.title is '菜单名称';
comment on column public.sys_menu.name is '路由名称';
comment on column public.sys_menu.path is '路由路径';
comment on column public.sys_menu.component is '组件路径';
comment on column public.sys_menu.icon is '菜单图标';
comment on column public.sys_menu.sort_order is '显示顺序';
comment on column public.sys_menu.type is '菜单类型（0目录 1菜单 2按钮）';
comment on column public.sys_menu.permission is '权限标识';
comment on column public.sys_menu.hidden is '是否隐藏';
comment on column public.sys_menu.keep_alive is '是否缓存';
comment on column public.sys_menu.redirect is '重定向地址';
comment on column public.sys_menu.del_flag is '逻辑删除标识';
comment on column public.sys_menu.create_time is '创建时间';
comment on column public.sys_menu.update_time is '更新时间';
comment on column public.sys_menu.status is '菜单状态（1正常 0停用）';

create index if not exists idx_sys_menu_parent_id on public.sys_menu (parent_id);
create index if not exists idx_sys_menu_sort on public.sys_menu (sort_order);
create index if not exists idx_sys_menu_status on public.sys_menu (status);

create table if not exists public.sys_user_role (
  user_id bigint not null,
  role_id bigint not null,
  primary key (user_id, role_id)
);

comment on table public.sys_user_role is '用户和角色关联表';
comment on column public.sys_user_role.user_id is '用户ID';
comment on column public.sys_user_role.role_id is '角色ID';

create index if not exists idx_sys_user_role_role_id on public.sys_user_role (role_id);

create table if not exists public.sys_role_menu (
  role_id bigint not null,
  menu_id bigint not null,
  primary key (role_id, menu_id)
);

comment on table public.sys_role_menu is '角色和菜单关联表';
comment on column public.sys_role_menu.role_id is '角色ID';
comment on column public.sys_role_menu.menu_id is '菜单ID';

create index if not exists idx_sys_role_menu_role_id on public.sys_role_menu (role_id);
create index if not exists idx_sys_role_menu_menu_id on public.sys_role_menu (menu_id);
