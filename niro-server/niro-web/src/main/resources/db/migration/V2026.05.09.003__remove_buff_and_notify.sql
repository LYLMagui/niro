/*
 * 变更日期: 2026-05-09
 * 目标: 移除旧 Buff 业务域与服务通知配置。
 * 幂等策略: 权限资源按稳定 permission_code/resource_key 清理，字段与表均使用 if exists。
 * 回滚思路: 需新增 migration 重建旧 Buff 表与通知字段，并从备份恢复历史数据；本项目当前不保留旧 Buff/通知运行时兼容。
 */
begin;

with removed_resources as (
    select id
    from public.resource_draft
    where resource_key in (
        'button_task_buff_list',
        'button_buff_account_save',
        'button_buff_account_delete',
        'button_buff_account_check',
        'button_buff_account_check_all',
        'button_settings_test_notify',
        'button_notify_send',
        'page_goods_list'
    )
       or permission_code in (
        'task:buff:list',
        'buff:account:save',
        'buff:account:delete',
        'buff:account:check',
        'buff:account:check:all',
        'system:settings:test-notify',
        'system:notify:send',
        'system:goods:sync'
    )
       or page_key = 'GoodsList'
)
delete from public.role_resource_draft rr
where rr.resource_id in (select id from removed_resources);

delete from public.resource_draft
where resource_key in (
    'button_task_buff_list',
    'button_buff_account_save',
    'button_buff_account_delete',
    'button_buff_account_check',
    'button_buff_account_check_all',
    'button_settings_test_notify',
    'button_notify_send',
    'page_goods_list'
)
   or permission_code in (
    'task:buff:list',
    'buff:account:save',
    'buff:account:delete',
    'buff:account:check',
    'buff:account:check:all',
    'system:settings:test-notify',
    'system:notify:send',
    'system:goods:sync'
)
   or page_key = 'GoodsList';

with removed_published_resources as (
    select id
    from public.resource_published
    where resource_key in (
        'button_task_buff_list',
        'button_buff_account_save',
        'button_buff_account_delete',
        'button_buff_account_check',
        'button_buff_account_check_all',
        'button_settings_test_notify',
        'button_notify_send',
        'page_goods_list'
    )
       or permission_code in (
        'task:buff:list',
        'buff:account:save',
        'buff:account:delete',
        'buff:account:check',
        'buff:account:check:all',
        'system:settings:test-notify',
        'system:notify:send',
        'system:goods:sync'
    )
       or page_key = 'GoodsList'
)
delete from public.role_resource_published rrp
where rrp.resource_id in (select id from removed_published_resources);

delete from public.resource_published
where resource_key in (
    'button_task_buff_list',
    'button_buff_account_save',
    'button_buff_account_delete',
    'button_buff_account_check',
    'button_buff_account_check_all',
    'button_settings_test_notify',
    'button_notify_send',
    'page_goods_list'
)
   or permission_code in (
    'task:buff:list',
    'buff:account:save',
    'buff:account:delete',
    'buff:account:check',
    'buff:account:check:all',
    'system:settings:test-notify',
    'system:notify:send',
    'system:goods:sync'
)
   or page_key = 'GoodsList';

alter table if exists public.user_platform_setting drop column if exists wecom_corpid;
alter table if exists public.user_platform_setting drop column if exists wecom_corpsecret;
alter table if exists public.user_platform_setting drop column if exists wecom_agentid;
alter table if exists public.user_platform_setting drop column if exists wecom_touser;
alter table if exists public.user_platform_setting drop column if exists email_enabled;
alter table if exists public.user_platform_setting drop column if exists email_host;
alter table if exists public.user_platform_setting drop column if exists email_port;
alter table if exists public.user_platform_setting drop column if exists email_account;
alter table if exists public.user_platform_setting drop column if exists email_password;
alter table if exists public.user_platform_setting drop column if exists email_receiver;

alter table if exists public.trade_order_record
    alter column platform set default 'C5';
comment on column public.trade_order_record.platform is '平台标识：C5';

alter table if exists public.user_platform_setting
    alter column payment_method set default 'BALANCE';
comment on column public.user_platform_setting.payment_method is '支付方式：BALANCE-余额，ALIPAY-支付宝，WECHAT-微信';

drop table if exists public.buff_scan_task_account;
drop table if exists public.buff_scan_task;
drop table if exists public.buff_leak_alert;
drop table if exists public.buff_price_history;
drop table if exists public.buff_goods_stats;
drop table if exists public.buff_sticker;
drop table if exists public.buff_goods;
drop table if exists public.buff_goods_category;
drop table if exists public.buff_account;

commit;

-- 复核 SQL:
-- select to_regclass('public.buff_account'), to_regclass('public.buff_goods'), to_regclass('public.buff_scan_task');
-- select column_name from information_schema.columns where table_schema = 'public' and table_name = 'user_platform_setting' and (column_name like 'wecom_%' or column_name like 'email_%');
-- select permission_code from public.resource_published where permission_code in ('task:buff:list', 'buff:account:save', 'system:settings:test-notify', 'system:notify:send');
