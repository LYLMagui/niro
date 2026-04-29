-- ============================================================
-- C5 AppKey 加密存储字段（PostgreSQL · 追加式 migration）
-- 日期: 2026-04-29
-- 目标:
-- 1. 为 c5_sniping_account 新增 AppKey 密文、脱敏值与迁移时间字段
-- 2. 为 user_platform_setting 新增历史全局 AppKey 密文、脱敏值与迁移时间字段
-- 3. 支撑业务从明文 c5_app_key 迁移到 AES-GCM 加密存储
-- 幂等策略:
-- - 使用 add column if not exists，重复执行不报错
-- - 字段注释重复执行结果一致
-- - 不在 SQL 中处理历史明文加密，避免密钥进入脚本或数据库函数
-- 回滚思路:
-- - 如需回滚，可新增 migration 让业务临时回读 c5_app_key，并软弃用新增字段
-- ============================================================

begin;

alter table public.c5_sniping_account
    add column if not exists c5_app_key_encrypted text not null default '',
    add column if not exists c5_app_key_masked varchar(64) not null default '',
    add column if not exists c5_app_key_migrated_at timestamp null default null;

comment on column public.c5_sniping_account.c5_app_key_encrypted is '账号级C5 AppKey密文，格式为v1:base64(iv):base64(ciphertextWithTag)';
comment on column public.c5_sniping_account.c5_app_key_masked is '账号级C5 AppKey脱敏展示值';
comment on column public.c5_sniping_account.c5_app_key_migrated_at is '账号级C5 AppKey历史明文迁移时间';

alter table public.user_platform_setting
    add column if not exists c5_app_key_encrypted text not null default '',
    add column if not exists c5_app_key_masked varchar(64) not null default '',
    add column if not exists c5_app_key_migrated_at timestamp null default null;

comment on column public.user_platform_setting.c5_app_key_encrypted is '用户全局C5 AppKey密文，格式为v1:base64(iv):base64(ciphertextWithTag)';
comment on column public.user_platform_setting.c5_app_key_masked is '用户全局C5 AppKey脱敏展示值';
comment on column public.user_platform_setting.c5_app_key_migrated_at is '用户全局C5 AppKey历史明文迁移时间';

commit;

-- 复核 SQL：
-- select count(*) from public.c5_sniping_account where c5_app_key <> '' and c5_app_key_encrypted = '';
-- select count(*) from public.user_platform_setting where c5_app_key <> '' and c5_app_key_encrypted = '';
