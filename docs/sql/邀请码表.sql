create table sys_invite_code (
  id bigint generated always as identity primary key,
  code varchar(32) not null default '',
  issuer_user_id bigint not null default 0,
  max_use_count integer not null default 1,
  used_count integer not null default 0,
  status smallint not null default 1,
  expire_time timestamp not null default '9999-12-31 23:59:59',
  remark varchar(200) not null default '',
  created_at timestamp not null default now(),
  updated_at timestamp not null default now(),
  constraint uk_sys_invite_code_code unique (code),
  constraint chk_sys_invite_code_status check (status in (0, 1)),
  constraint chk_sys_invite_code_max_use_count check (max_use_count >= 1),
  constraint chk_sys_invite_code_used_count check (used_count >= 0 and used_count <= max_use_count)
);

comment on table sys_invite_code is '系统邀请码表';
comment on column sys_invite_code.id is '主键';
comment on column sys_invite_code.code is '邀请码';
comment on column sys_invite_code.issuer_user_id is '签发人用户id，0 表示系统签发';
comment on column sys_invite_code.max_use_count is '允许使用的最大次数';
comment on column sys_invite_code.used_count is '已使用次数';
comment on column sys_invite_code.status is '状态：1 启用 0 停用';
comment on column sys_invite_code.expire_time is '过期时间，9999-12-31 23:59:59 表示永不过期';
comment on column sys_invite_code.remark is '备注';
comment on column sys_invite_code.created_at is '创建时间';
comment on column sys_invite_code.updated_at is '更新时间';

insert into sys_invite_code (code, max_use_count, remark)
values
  ('NIRO-INVITE-001', 1, '一次性邀请码示例'),
  ('NIRO-INVITE-TEAM-001', 20, '团队邀请码示例');
