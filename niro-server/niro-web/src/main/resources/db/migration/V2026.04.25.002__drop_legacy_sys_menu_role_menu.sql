-- ============================================================
-- 删除旧权限菜单表（PostgreSQL · 追加式 migration）
-- 日期: 2026-04-25
-- 目标:
-- 1. 补齐新权限系统按钮资源与 admin 授权
-- 2. 下线旧权限系统菜单与角色菜单授权表
-- 3. 保留 sys_user、sys_role、sys_user_role，继续作为用户、角色与用户角色关系真源
-- 4. 运行时权限统一使用 resource_published 与 role_resource_published
-- 幂等策略:
-- - 资源按 resource_key 做 upsert，不依赖自增 id
-- - admin 授权按 role_id + resource_id 做 not exists 保护
-- - 使用 drop table if exists，允许表已不存在时重复执行不报错
-- - 先删除 sys_role_menu，再删除 sys_menu，避免逻辑依赖残留
-- 回滚思路:
-- - 删除后旧菜单权限数据不再可直接恢复
-- - 如需恢复，只能新增 migration 重建旧表结构，并从备份或补偿脚本恢复数据
-- ============================================================

begin;

with button_seed(resource_key, parent_resource_key, title, permission_code, button_group, sort_order, remark) as (
    values
        ('button_task_scan_list', 'page_task_list', '任务扫描查看', 'task:scan:list', '扫货', 10, '任务扫描列表权限'),
        ('button_task_buff_list', 'page_task_list', 'Buff任务查看', 'task:buff:list', '扫货', 20, 'Buff任务兼容权限'),
        ('button_task_record_list', 'page_order_record', '订单记录查看', 'task:record:list', '订单', 10, '订单记录列表权限'),
        ('button_task_inventory_view', 'page_inventory_board', '库存查看', 'task:inventory:view', '库存', 10, '库存看板查看权限'),
        ('button_task_c5_list', 'page_c5_sniping_task_v2', 'C5任务查看', 'task:c5:list', '扫货', 10, 'C5任务查看权限'),
        ('button_log_list', 'logs_new', '日志查看', 'system:logs:list', '系统', 10, '系统日志查看权限'),
        ('button_account_list', 'page_settings', '账号查看', 'system:account:list', '账号', 10, '账号列表查看权限'),
        ('button_goods_list', 'page_goods_list', '商品查看', 'system:goods:list', '商品', 10, '商品列表查看权限'),
        ('button_sticker_list', 'page_sticker_list', '贴纸查看', 'system:sticker:list', '商品', 10, '贴纸列表查看权限'),
        ('button_sticker_sync', 'page_sticker_list', '贴纸同步', 'system:sticker:sync', '商品', 20, '贴纸同步权限'),
        ('button_buff_account_save', 'page_settings', '保存账号', 'buff:account:save', '账号', 20, 'Buff账号保存权限'),
        ('button_buff_account_delete', 'page_settings', '删除账号', 'buff:account:delete', '账号', 30, 'Buff账号删除权限'),
        ('button_buff_account_check', 'page_settings', '检测账号', 'buff:account:check', '账号', 40, 'Buff账号检测权限'),
        ('button_buff_account_check_all', 'page_settings', '批量检测账号', 'buff:account:check:all', '账号', 50, 'Buff账号批量检测权限'),
        ('button_order_record_update', 'page_order_record', '更新订单', 'order:record:update', '订单', 20, '订单记录更新权限'),
        ('button_order_record_delete', 'page_order_record', '删除订单', 'order:record:delete', '订单', 30, '订单记录删除权限'),
        ('button_settings_save', 'page_settings', '保存设置', 'system:settings:save', '系统', 60, '系统设置保存权限'),
        ('button_settings_test_notify', 'page_settings', '测试通知', 'system:settings:test-notify', '系统', 70, '测试通知权限'),
        ('button_invite_code_manage', 'invite_code_manage_new', '邀请码管理', 'system:invite-code:manage', '系统', 10, '邀请码管理权限'),
        ('button_notify_send', 'page_settings', '发送通知', 'system:notify:send', '系统', 80, '通知发送权限'),
        ('button_permission_manage', 'permission_manage_new', '权限工作台操作', 'system:permission:manage', '系统', 10, '新权限工作台操作权限')
), prepared_button as (
    select
        bs.resource_key,
        parent.id as parent_resource_id,
        bs.title,
        bs.permission_code,
        bs.button_group,
        bs.sort_order,
        bs.remark
    from button_seed bs
    join public.resource_draft parent
        on parent.resource_key = bs.parent_resource_key
)
insert into public.resource_draft (
    resource_key,
    resource_type,
    parent_resource_id,
    page_key,
    title,
    icon,
    sort_order,
    hidden,
    permission_code,
    button_group,
    remark,
    status,
    created_by,
    created_at,
    updated_by,
    updated_at,
    del_flag
)
select
    pb.resource_key,
    'BUTTON',
    pb.parent_resource_id,
    '',
    pb.title,
    '',
    pb.sort_order,
    false,
    pb.permission_code,
    pb.button_group,
    pb.remark,
    1,
    'flyway',
    now(),
    'flyway',
    now(),
    0
from prepared_button pb
on conflict (resource_key) do update set
    resource_type = excluded.resource_type,
    parent_resource_id = excluded.parent_resource_id,
    page_key = excluded.page_key,
    title = excluded.title,
    icon = excluded.icon,
    sort_order = excluded.sort_order,
    hidden = excluded.hidden,
    permission_code = excluded.permission_code,
    button_group = excluded.button_group,
    remark = excluded.remark,
    status = excluded.status,
    updated_by = excluded.updated_by,
    updated_at = excluded.updated_at,
    del_flag = excluded.del_flag;

with button_seed(resource_key, parent_resource_key, title, permission_code, button_group, sort_order, remark) as (
    values
        ('button_task_scan_list', 'page_task_list', '任务扫描查看', 'task:scan:list', '扫货', 10, '任务扫描列表权限'),
        ('button_task_buff_list', 'page_task_list', 'Buff任务查看', 'task:buff:list', '扫货', 20, 'Buff任务兼容权限'),
        ('button_task_record_list', 'page_order_record', '订单记录查看', 'task:record:list', '订单', 10, '订单记录列表权限'),
        ('button_task_inventory_view', 'page_inventory_board', '库存查看', 'task:inventory:view', '库存', 10, '库存看板查看权限'),
        ('button_task_c5_list', 'page_c5_sniping_task_v2', 'C5任务查看', 'task:c5:list', '扫货', 10, 'C5任务查看权限'),
        ('button_log_list', 'logs_new', '日志查看', 'system:logs:list', '系统', 10, '系统日志查看权限'),
        ('button_account_list', 'page_settings', '账号查看', 'system:account:list', '账号', 10, '账号列表查看权限'),
        ('button_goods_list', 'page_goods_list', '商品查看', 'system:goods:list', '商品', 10, '商品列表查看权限'),
        ('button_sticker_list', 'page_sticker_list', '贴纸查看', 'system:sticker:list', '商品', 10, '贴纸列表查看权限'),
        ('button_sticker_sync', 'page_sticker_list', '贴纸同步', 'system:sticker:sync', '商品', 20, '贴纸同步权限'),
        ('button_buff_account_save', 'page_settings', '保存账号', 'buff:account:save', '账号', 20, 'Buff账号保存权限'),
        ('button_buff_account_delete', 'page_settings', '删除账号', 'buff:account:delete', '账号', 30, 'Buff账号删除权限'),
        ('button_buff_account_check', 'page_settings', '检测账号', 'buff:account:check', '账号', 40, 'Buff账号检测权限'),
        ('button_buff_account_check_all', 'page_settings', '批量检测账号', 'buff:account:check:all', '账号', 50, 'Buff账号批量检测权限'),
        ('button_order_record_update', 'page_order_record', '更新订单', 'order:record:update', '订单', 20, '订单记录更新权限'),
        ('button_order_record_delete', 'page_order_record', '删除订单', 'order:record:delete', '订单', 30, '订单记录删除权限'),
        ('button_settings_save', 'page_settings', '保存设置', 'system:settings:save', '系统', 60, '系统设置保存权限'),
        ('button_settings_test_notify', 'page_settings', '测试通知', 'system:settings:test-notify', '系统', 70, '测试通知权限'),
        ('button_invite_code_manage', 'invite_code_manage_new', '邀请码管理', 'system:invite-code:manage', '系统', 10, '邀请码管理权限'),
        ('button_notify_send', 'page_settings', '发送通知', 'system:notify:send', '系统', 80, '通知发送权限'),
        ('button_permission_manage', 'permission_manage_new', '权限工作台操作', 'system:permission:manage', '系统', 10, '新权限工作台操作权限')
), prepared_button as (
    select
        bs.resource_key,
        parent.id as parent_resource_id,
        bs.title,
        bs.permission_code,
        bs.button_group,
        bs.sort_order,
        bs.remark
    from button_seed bs
    join public.resource_published parent
        on parent.resource_key = bs.parent_resource_key
)
insert into public.resource_published (
    resource_key,
    resource_type,
    parent_resource_id,
    page_key,
    title,
    icon,
    sort_order,
    hidden,
    permission_code,
    button_group,
    remark,
    status,
    created_by,
    created_at,
    updated_by,
    updated_at,
    del_flag
)
select
    pb.resource_key,
    'BUTTON',
    pb.parent_resource_id,
    '',
    pb.title,
    '',
    pb.sort_order,
    false,
    pb.permission_code,
    pb.button_group,
    pb.remark,
    1,
    'flyway',
    now(),
    'flyway',
    now(),
    0
from prepared_button pb
on conflict (resource_key) do update set
    resource_type = excluded.resource_type,
    parent_resource_id = excluded.parent_resource_id,
    page_key = excluded.page_key,
    title = excluded.title,
    icon = excluded.icon,
    sort_order = excluded.sort_order,
    hidden = excluded.hidden,
    permission_code = excluded.permission_code,
    button_group = excluded.button_group,
    remark = excluded.remark,
    status = excluded.status,
    updated_by = excluded.updated_by,
    updated_at = excluded.updated_at,
    del_flag = excluded.del_flag;

with admin_role as (
    select role_id
    from public.sys_role
    where role_key = 'admin'
      and del_flag = 0
    order by role_id
    limit 1
), button_resource as (
    select id
    from public.resource_draft
    where resource_type = 'BUTTON'
      and del_flag = 0
)
insert into public.role_resource_draft (
    role_id,
    resource_id,
    created_by,
    created_at,
    updated_by,
    updated_at,
    del_flag
)
select
    ar.role_id,
    br.id,
    'flyway',
    now(),
    'flyway',
    now(),
    0
from admin_role ar
cross join button_resource br
where not exists (
    select 1
    from public.role_resource_draft rrd
    where rrd.role_id = ar.role_id
      and rrd.resource_id = br.id
);

with admin_role as (
    select role_id
    from public.sys_role
    where role_key = 'admin'
      and del_flag = 0
    order by role_id
    limit 1
), button_resource as (
    select id
    from public.resource_published
    where resource_type = 'BUTTON'
      and del_flag = 0
)
insert into public.role_resource_published (
    role_id,
    resource_id,
    created_by,
    created_at,
    updated_by,
    updated_at,
    del_flag
)
select
    ar.role_id,
    br.id,
    'flyway',
    now(),
    'flyway',
    now(),
    0
from admin_role ar
cross join button_resource br
where not exists (
    select 1
    from public.role_resource_published rrp
    where rrp.role_id = ar.role_id
      and rrp.resource_id = br.id
);

drop table if exists public.sys_role_menu;
drop table if exists public.sys_menu;

commit;
