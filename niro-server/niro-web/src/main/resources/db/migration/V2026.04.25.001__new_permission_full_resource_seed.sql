-- ============================================================
-- 新权限系统全量资源种子（PostgreSQL · 追加式 migration）
-- 日期: 2026-04-25
-- 目标:
-- 1. 修正新权限资源 page_key 校验，支持目录型 MENU 不绑定 page_key
-- 2. 注入全量后台目录与页面资源到草稿态和发布态
-- 3. 为 admin 角色补齐新权限资源授权，确保新导航切换具备完整后台菜单
-- 幂等说明:
-- - 约束按名称先删除再重建，保证从旧模型迁移到目录型 MENU 模型
-- - 资源按 resource_key 做 upsert，不依赖自增 id
-- - 父子关系通过稳定 resource_key 查询父资源 id
-- - admin 授权按 role_id + resource_id 做 not exists 保护
-- 回滚思路:
-- - 如需回滚，可新增 migration 软删除本次 resource_key 对应资源，并清理对应 role_resource 授权
-- - 如需恢复旧约束，可新增 migration 重建旧 page_key 校验，但会再次禁止目录型 MENU
-- ============================================================

begin;

do $$
begin
    if exists (
        select 1
        from pg_constraint
        where conname = 'chk_resource_draft_page_key_by_type'
          and conrelid = 'public.resource_draft'::regclass
    ) then
        alter table public.resource_draft
            drop constraint chk_resource_draft_page_key_by_type;
    end if;

    if exists (
        select 1
        from pg_constraint
        where conname = 'chk_resource_published_page_key_by_type'
          and conrelid = 'public.resource_published'::regclass
    ) then
        alter table public.resource_published
            drop constraint chk_resource_published_page_key_by_type;
    end if;

    if not exists (
        select 1
        from pg_constraint
        where conname = 'chk_resource_draft_page_key_by_type'
          and conrelid = 'public.resource_draft'::regclass
    ) then
        alter table public.resource_draft
            add constraint chk_resource_draft_page_key_by_type
                check (
                    (resource_type = 'BUTTON' and page_key = '')
                    or (resource_type = 'PAGE' and page_key <> '')
                    or resource_type = 'MENU'
                );
    end if;

    if not exists (
        select 1
        from pg_constraint
        where conname = 'chk_resource_published_page_key_by_type'
          and conrelid = 'public.resource_published'::regclass
    ) then
        alter table public.resource_published
            add constraint chk_resource_published_page_key_by_type
                check (
                    (resource_type = 'BUTTON' and page_key = '')
                    or (resource_type = 'PAGE' and page_key <> '')
                    or resource_type = 'MENU'
                );
    end if;
end $$;

with menu_seed(resource_key, title, icon, sort_order, remark) as (
    values
        ('nav_dashboard', '工作台', 'dashboard', 10, '全量替换阶段顶级目录'),
        ('nav_scan', '扫货', 'search', 20, '全量替换阶段顶级目录'),
        ('nav_order', '订单', 'history', 30, '全量替换阶段顶级目录'),
        ('nav_unbox', '开箱', 'gift', 40, '全量替换阶段顶级目录'),
        ('nav_goods', '商品库', 'shop', 50, '全量替换阶段顶级目录'),
        ('nav_system', '系统', 'setting', 60, '全量替换阶段顶级目录')
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
    ms.resource_key,
    'MENU',
    0,
    '',
    ms.title,
    ms.icon,
    ms.sort_order,
    false,
    '',
    '',
    ms.remark,
    1,
    'flyway',
    now(),
    'flyway',
    now(),
    0
from menu_seed ms
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

with page_seed(resource_key, parent_resource_key, page_key, title, icon, sort_order, remark) as (
    values
        ('page_dashboard', 'nav_dashboard', 'Dashboard', '仪表盘', 'dashboard', 10, '后台首页页面'),
        ('page_task_list', 'nav_scan', 'TaskList', '任务列表', 'task', 10, '扫货任务列表页面'),
        ('page_task_config', 'nav_scan', 'TaskConfig', '任务配置', 'setting', 20, '扫货任务配置页面'),
        ('page_c5_sniping_task_v2', 'nav_scan', 'C5SnipingTaskV2', 'C5扫货2.0', 'search', 30, 'C5扫货2.0页面'),
        ('page_order_record', 'nav_order', 'OrderRecord', '订单记录', 'order', 10, '订单记录页面'),
        ('page_profit_stats', 'nav_order', 'ProfitStats', '利润统计', 'chart', 20, '利润统计页面'),
        ('page_unbox_record', 'nav_unbox', 'UnboxRecord', '开箱记录', 'gift', 10, '开箱记录页面'),
        ('page_goods_list', 'nav_goods', 'GoodsList', '商品列表', 'shop', 10, '商品列表页面'),
        ('page_sticker_list', 'nav_goods', 'StickerList', '贴纸列表', 'layers', 20, '贴纸列表页面'),
        ('page_inventory_board', 'nav_goods', 'InventoryBoard', '库存看板', 'view-module', 30, '库存看板页面'),
        ('page_settings', 'nav_system', 'Settings', '系统设置', 'setting', 10, '系统设置页面'),
        ('invite_code_manage_new', 'nav_system', 'InviteCodeManageNew', '邀请码管理', 'qrcode', 20, '邀请码管理页面'),
        ('logs_new', 'nav_system', 'LogsNew', '全链路日志', 'file-search', 30, '全链路日志页面'),
        ('permission_manage_new', 'nav_system', 'PermissionManageNew', '新权限管理', 'control', 40, '新权限管理页面')
), prepared_page as (
    select
        ps.resource_key,
        parent.id as parent_resource_id,
        ps.page_key,
        ps.title,
        ps.icon,
        ps.sort_order,
        ps.remark
    from page_seed ps
    join public.resource_draft parent
        on parent.resource_key = ps.parent_resource_key
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
    pp.resource_key,
    'PAGE',
    pp.parent_resource_id,
    pp.page_key,
    pp.title,
    pp.icon,
    pp.sort_order,
    false,
    '',
    '',
    pp.remark,
    1,
    'flyway',
    now(),
    'flyway',
    now(),
    0
from prepared_page pp
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

with menu_seed(resource_key, title, icon, sort_order, remark) as (
    values
        ('nav_dashboard', '工作台', 'dashboard', 10, '全量替换阶段顶级目录'),
        ('nav_scan', '扫货', 'search', 20, '全量替换阶段顶级目录'),
        ('nav_order', '订单', 'history', 30, '全量替换阶段顶级目录'),
        ('nav_unbox', '开箱', 'gift', 40, '全量替换阶段顶级目录'),
        ('nav_goods', '商品库', 'shop', 50, '全量替换阶段顶级目录'),
        ('nav_system', '系统', 'setting', 60, '全量替换阶段顶级目录')
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
    ms.resource_key,
    'MENU',
    0,
    '',
    ms.title,
    ms.icon,
    ms.sort_order,
    false,
    '',
    '',
    ms.remark,
    1,
    'flyway',
    now(),
    'flyway',
    now(),
    0
from menu_seed ms
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

with page_seed(resource_key, parent_resource_key, page_key, title, icon, sort_order, remark) as (
    values
        ('page_dashboard', 'nav_dashboard', 'Dashboard', '仪表盘', 'dashboard', 10, '后台首页页面'),
        ('page_task_list', 'nav_scan', 'TaskList', '任务列表', 'task', 10, '扫货任务列表页面'),
        ('page_task_config', 'nav_scan', 'TaskConfig', '任务配置', 'setting', 20, '扫货任务配置页面'),
        ('page_c5_sniping_task_v2', 'nav_scan', 'C5SnipingTaskV2', 'C5扫货2.0', 'search', 30, 'C5扫货2.0页面'),
        ('page_order_record', 'nav_order', 'OrderRecord', '订单记录', 'order', 10, '订单记录页面'),
        ('page_profit_stats', 'nav_order', 'ProfitStats', '利润统计', 'chart', 20, '利润统计页面'),
        ('page_unbox_record', 'nav_unbox', 'UnboxRecord', '开箱记录', 'gift', 10, '开箱记录页面'),
        ('page_goods_list', 'nav_goods', 'GoodsList', '商品列表', 'shop', 10, '商品列表页面'),
        ('page_sticker_list', 'nav_goods', 'StickerList', '贴纸列表', 'layers', 20, '贴纸列表页面'),
        ('page_inventory_board', 'nav_goods', 'InventoryBoard', '库存看板', 'view-module', 30, '库存看板页面'),
        ('page_settings', 'nav_system', 'Settings', '系统设置', 'setting', 10, '系统设置页面'),
        ('invite_code_manage_new', 'nav_system', 'InviteCodeManageNew', '邀请码管理', 'qrcode', 20, '邀请码管理页面'),
        ('logs_new', 'nav_system', 'LogsNew', '全链路日志', 'file-search', 30, '全链路日志页面'),
        ('permission_manage_new', 'nav_system', 'PermissionManageNew', '新权限管理', 'control', 40, '新权限管理页面')
), prepared_page as (
    select
        ps.resource_key,
        parent.id as parent_resource_id,
        ps.page_key,
        ps.title,
        ps.icon,
        ps.sort_order,
        ps.remark
    from page_seed ps
    join public.resource_published parent
        on parent.resource_key = ps.parent_resource_key
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
    pp.resource_key,
    'PAGE',
    pp.parent_resource_id,
    pp.page_key,
    pp.title,
    pp.icon,
    pp.sort_order,
    false,
    '',
    '',
    pp.remark,
    1,
    'flyway',
    now(),
    'flyway',
    now(),
    0
from prepared_page pp
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

with target_resource(resource_key) as (
    values
        ('nav_dashboard'),
        ('page_dashboard'),
        ('nav_scan'),
        ('page_task_list'),
        ('page_task_config'),
        ('page_c5_sniping_task_v2'),
        ('nav_order'),
        ('page_order_record'),
        ('page_profit_stats'),
        ('nav_unbox'),
        ('page_unbox_record'),
        ('nav_goods'),
        ('page_goods_list'),
        ('page_sticker_list'),
        ('page_inventory_board'),
        ('nav_system'),
        ('page_settings'),
        ('invite_code_manage_new'),
        ('logs_new'),
        ('permission_manage_new')
), admin_role as (
    select role_id
    from public.sys_role
    where role_key = 'admin'
      and del_flag = 0
    order by role_id
    limit 1
), full_resource as (
    select rd.id
    from public.resource_draft rd
    join target_resource tr on tr.resource_key = rd.resource_key
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
    fr.id,
    'flyway',
    now(),
    'flyway',
    now(),
    0
from admin_role ar
cross join full_resource fr
where not exists (
    select 1
    from public.role_resource_draft rrd
    where rrd.role_id = ar.role_id
      and rrd.resource_id = fr.id
);

with target_resource(resource_key) as (
    values
        ('nav_dashboard'),
        ('page_dashboard'),
        ('nav_scan'),
        ('page_task_list'),
        ('page_task_config'),
        ('page_c5_sniping_task_v2'),
        ('nav_order'),
        ('page_order_record'),
        ('page_profit_stats'),
        ('nav_unbox'),
        ('page_unbox_record'),
        ('nav_goods'),
        ('page_goods_list'),
        ('page_sticker_list'),
        ('page_inventory_board'),
        ('nav_system'),
        ('page_settings'),
        ('invite_code_manage_new'),
        ('logs_new'),
        ('permission_manage_new')
), admin_role as (
    select role_id
    from public.sys_role
    where role_key = 'admin'
      and del_flag = 0
    order by role_id
    limit 1
), full_resource as (
    select rp.id
    from public.resource_published rp
    join target_resource tr on tr.resource_key = rp.resource_key
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
    fr.id,
    'flyway',
    now(),
    'flyway',
    now(),
    0
from admin_role ar
cross join full_resource fr
where not exists (
    select 1
    from public.role_resource_published rrp
    where rrp.role_id = ar.role_id
      and rrp.resource_id = fr.id
);

commit;

-- ============================================================
-- 执行后复核（可选）
-- ============================================================
-- select resource_key, resource_type, parent_resource_id, page_key, title, sort_order, status, del_flag
-- from public.resource_published
-- where resource_key in (
--     'nav_dashboard', 'page_dashboard', 'nav_scan', 'page_task_list', 'page_task_config', 'page_c5_sniping_task_v2',
--     'nav_order', 'page_order_record', 'page_profit_stats', 'nav_unbox', 'page_unbox_record',
--     'nav_goods', 'page_goods_list', 'page_sticker_list', 'page_inventory_board',
--     'nav_system', 'page_settings', 'invite_code_manage_new', 'logs_new', 'permission_manage_new'
-- )
-- order by parent_resource_id, sort_order, id;
--
-- select r.role_key, count(*) as granted_count
-- from public.role_resource_published rrp
-- join public.sys_role r on r.role_id = rrp.role_id and r.del_flag = 0
-- join public.resource_published rp on rp.id = rrp.resource_id
-- where r.role_key = 'admin'
--   and rp.resource_key in (
--     'nav_dashboard', 'page_dashboard', 'nav_scan', 'page_task_list', 'page_task_config', 'page_c5_sniping_task_v2',
--     'nav_order', 'page_order_record', 'page_profit_stats', 'nav_unbox', 'page_unbox_record',
--     'nav_goods', 'page_goods_list', 'page_sticker_list', 'page_inventory_board',
--     'nav_system', 'page_settings', 'invite_code_manage_new', 'logs_new', 'permission_manage_new'
--   )
-- group by r.role_key;
