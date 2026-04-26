-- ============================================================
-- 补齐新权限系统按钮资源（PostgreSQL · 追加式 migration）
-- 日期: 2026-04-26
-- 目标:
-- 1. 补齐后台业务页面缺失的 BUTTON 资源
-- 2. 同步写入 resource_draft 与 resource_published，确保草稿态和发布态按钮权限码一致
-- 3. 给 role_key = 'admin' 角色补齐 draft / published 授权
-- 幂等策略:
-- - 资源按稳定 resource_key 做 upsert，不依赖自增 id
-- - 父级通过 parent_resource_key 解析，重复执行只更新资源属性
-- - admin 授权按 role_id + resource_id 做 not exists 保护
-- 回滚思路:
-- - 如需回滚，可新增 migration 将本脚本内 resource_key 对应资源软删除并清理授权
-- - 本脚本不删除、不 drop 表列，影响面仅限新增或修正按钮资源与 admin 授权
-- ============================================================

begin;

do $$
declare
    missing_parent text;
begin
    select parent_resource_key
    into missing_parent
    from (
        values
            ('page_dashboard'),
            ('page_goods_list'),
            ('page_order_record'),
            ('page_settings'),
            ('invite_code_manage_new'),
            ('permission_manage_new'),
            ('page_c5_sniping_task_v2'),
            ('page_c5_sniping_account_config'),
            ('page_unbox_record')
    ) required_parent(parent_resource_key)
    where not exists (
        select 1
        from public.resource_draft rd
        where rd.resource_key = required_parent.parent_resource_key
          and rd.del_flag = 0
    )
    limit 1;

    if missing_parent is not null then
        raise exception '迁移终止：resource_draft 缺少父级资源 %，请先执行新权限全量资源种子', missing_parent;
    end if;

    select parent_resource_key
    into missing_parent
    from (
        values
            ('page_dashboard'),
            ('page_goods_list'),
            ('page_order_record'),
            ('page_settings'),
            ('invite_code_manage_new'),
            ('permission_manage_new'),
            ('page_c5_sniping_task_v2'),
            ('page_c5_sniping_account_config'),
            ('page_unbox_record')
    ) required_parent(parent_resource_key)
    where not exists (
        select 1
        from public.resource_published rp
        where rp.resource_key = required_parent.parent_resource_key
          and rp.del_flag = 0
    )
    limit 1;

    if missing_parent is not null then
        raise exception '迁移终止：resource_published 缺少父级资源 %，请先执行新权限全量资源种子', missing_parent;
    end if;
end $$;

with button_seed(resource_key, parent_resource_key, title, permission_code, button_group, sort_order, remark) as (
    values
        ('button_task_scan_start', 'page_dashboard', '启动任务', 'task:scan:start', '运行状态', 10, '仪表盘启动任务权限'),
        ('button_task_scan_stop', 'page_dashboard', '停止任务', 'task:scan:stop', '运行状态', 20, '仪表盘停止任务权限'),
        ('button_goods_sync', 'page_goods_list', '同步分类商品', 'system:goods:sync', '工具栏', 20, '商品列表同步分类商品权限'),
        ('button_task_scan_create', 'page_goods_list', '扫货入口', 'task:scan:create', '工具栏', 30, '商品列表进入扫货创建权限'),
        ('button_order_c5_sync', 'page_order_record', '同步 C5 订单', 'order:c5:sync', '工具栏', 40, '订单记录同步 C5 订单权限'),
        ('button_invite_code_create', 'invite_code_manage_new', '新建邀请码', 'system:invite-code:create', '工具栏', 20, '新建邀请码权限'),
        ('button_invite_code_batch_create', 'invite_code_manage_new', '批量生成邀请码', 'system:invite-code:batch-create', '工具栏', 30, '批量生成邀请码权限'),
        ('button_invite_code_update', 'invite_code_manage_new', '编辑邀请码', 'system:invite-code:update', '行操作', 40, '编辑邀请码权限'),
        ('button_invite_code_enable', 'invite_code_manage_new', '启用邀请码', 'system:invite-code:enable', '行操作', 50, '启用邀请码权限'),
        ('button_invite_code_disable', 'invite_code_manage_new', '停用邀请码', 'system:invite-code:disable', '行操作', 60, '停用邀请码权限'),
        ('button_invite_code_copy', 'invite_code_manage_new', '复制邀请码', 'system:invite-code:copy', '行操作', 70, '复制邀请码权限'),
        ('button_permission_resource_read', 'permission_manage_new', '资源读取', 'system:permission:resource:read', '基础读取', 10, '读取新权限草稿资源权限'),
        ('button_permission_role_auth_read', 'permission_manage_new', '角色授权读取', 'system:permission:role-auth:read', '基础读取', 20, '读取角色授权与用户角色权限'),
        ('button_permission_manage', 'permission_manage_new', '权限工作台入口', 'system:permission:manage', '基础读取', 30, '新权限工作台入口权限'),
        ('button_permission_resource_save', 'permission_manage_new', '资源保存', 'system:permission:resource:save', '资源工作台', 40, '保存新权限草稿资源权限'),
        ('button_permission_role_create', 'permission_manage_new', '角色创建', 'system:permission:role:create', '角色', 50, '创建角色权限'),
        ('button_permission_role_update', 'permission_manage_new', '角色编辑', 'system:permission:role:update', '角色', 60, '编辑角色权限'),
        ('button_permission_role_delete', 'permission_manage_new', '角色删除', 'system:permission:role:delete', '角色', 70, '删除角色权限'),
        ('button_permission_role_copy', 'permission_manage_new', '角色复制', 'system:permission:role:copy', '角色', 80, '复制角色权限'),
        ('button_permission_role_auth_save', 'permission_manage_new', '角色授权保存', 'system:permission:role-auth:save', '角色授权', 90, '保存角色授权草稿权限'),
        ('button_permission_user_assign', 'permission_manage_new', '用户分配角色', 'system:permission:user:assign', '用户角色', 100, '用户分配角色权限'),
        ('button_permission_role_preview', 'permission_manage_new', '角色预览', 'system:permission:role:preview', '发布', 110, '预览角色权限'),
        ('button_permission_publish_validate', 'permission_manage_new', '发布校验', 'system:permission:publish:validate', '发布', 120, '发布前校验权限'),
        ('button_permission_publish', 'permission_manage_new', '执行发布', 'system:permission:publish', '发布', 130, '执行发布权限'),
        ('button_c5_sniping_task_create', 'page_c5_sniping_task_v2', '新增扫货任务', 'c5:sniping-task:create', '工具栏', 20, 'C5扫货2.0新增任务权限'),
        ('button_c5_sniping_task_update', 'page_c5_sniping_task_v2', '编辑扫货任务', 'c5:sniping-task:update', '行操作', 30, 'C5扫货2.0编辑任务权限'),
        ('button_c5_sniping_task_enable', 'page_c5_sniping_task_v2', '开启扫货任务', 'c5:sniping-task:enable', '行操作', 40, 'C5扫货2.0开启任务权限'),
        ('button_c5_sniping_task_disable', 'page_c5_sniping_task_v2', '停止扫货任务', 'c5:sniping-task:disable', '行操作', 50, 'C5扫货2.0停止任务权限'),
        ('button_c5_sniping_task_delete', 'page_c5_sniping_task_v2', '删除扫货任务', 'c5:sniping-task:delete', '行操作', 60, 'C5扫货2.0删除任务权限'),
        ('button_c5_sniping_task_detail', 'page_c5_sniping_task_v2', '任务记录详情', 'c5:sniping-task:detail', '详情', 70, 'C5扫货2.0任务详情类记录入口权限'),
        ('button_c5_sniping_account_create', 'page_c5_sniping_account_config', '新增扫货账号', 'c5:sniping-account:create', '工具栏', 20, '新增 C5 扫货账号权限'),
        ('button_c5_sniping_account_update', 'page_c5_sniping_account_config', '编辑扫货账号', 'c5:sniping-account:update', '行操作', 30, '编辑 C5 扫货账号权限'),
        ('button_c5_sniping_account_delete', 'page_c5_sniping_account_config', '删除扫货账号', 'c5:sniping-account:delete', '行操作', 40, '删除 C5 扫货账号权限'),
        ('button_c5_sniping_account_detail', 'page_c5_sniping_account_config', '扫货账号详情', 'c5:sniping-account:detail', '详情', 50, 'C5 扫货账号详情权限'),
        ('button_unbox_record_create', 'page_unbox_record', '新建开箱批次', 'unbox:record:create', '批次', 20, '新建开箱记录批次权限'),
        ('button_unbox_record_update', 'page_unbox_record', '编辑开箱批次', 'unbox:record:update', '批次', 30, '编辑开箱记录批次权限'),
        ('button_unbox_record_delete', 'page_unbox_record', '删除开箱批次', 'unbox:record:delete', '批次', 40, '删除开箱记录批次权限'),
        ('button_unbox_record_ocr', 'page_unbox_record', 'OCR 识别', 'unbox:record:ocr', '明细', 50, '开箱记录 OCR 识别权限'),
        ('button_unbox_record_query_c5', 'page_unbox_record', 'C5 查询', 'unbox:record:query-c5', '明细', 60, '开箱记录 C5 挂单查询权限'),
        ('button_unbox_record_detail_add', 'page_unbox_record', '新增明细', 'unbox:record:detail:add', '明细', 70, '开箱记录新增明细权限'),
        ('button_unbox_record_detail_delete', 'page_unbox_record', '删除明细', 'unbox:record:detail:delete', '明细', 80, '开箱记录删除明细权限'),
        ('button_unbox_record_apply_price', 'page_unbox_record', '应用价格', 'unbox:record:apply-price', '明细', 90, '开箱记录应用价格权限'),
        ('button_unbox_record_apply_defaults', 'page_unbox_record', '应用默认值', 'unbox:record:apply-defaults', '明细', 100, '开箱记录应用默认值权限')
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
       and parent.del_flag = 0
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
        ('button_task_scan_start', 'page_dashboard', '启动任务', 'task:scan:start', '运行状态', 10, '仪表盘启动任务权限'),
        ('button_task_scan_stop', 'page_dashboard', '停止任务', 'task:scan:stop', '运行状态', 20, '仪表盘停止任务权限'),
        ('button_goods_sync', 'page_goods_list', '同步分类商品', 'system:goods:sync', '工具栏', 20, '商品列表同步分类商品权限'),
        ('button_task_scan_create', 'page_goods_list', '扫货入口', 'task:scan:create', '工具栏', 30, '商品列表进入扫货创建权限'),
        ('button_order_c5_sync', 'page_order_record', '同步 C5 订单', 'order:c5:sync', '工具栏', 40, '订单记录同步 C5 订单权限'),
        ('button_invite_code_create', 'invite_code_manage_new', '新建邀请码', 'system:invite-code:create', '工具栏', 20, '新建邀请码权限'),
        ('button_invite_code_batch_create', 'invite_code_manage_new', '批量生成邀请码', 'system:invite-code:batch-create', '工具栏', 30, '批量生成邀请码权限'),
        ('button_invite_code_update', 'invite_code_manage_new', '编辑邀请码', 'system:invite-code:update', '行操作', 40, '编辑邀请码权限'),
        ('button_invite_code_enable', 'invite_code_manage_new', '启用邀请码', 'system:invite-code:enable', '行操作', 50, '启用邀请码权限'),
        ('button_invite_code_disable', 'invite_code_manage_new', '停用邀请码', 'system:invite-code:disable', '行操作', 60, '停用邀请码权限'),
        ('button_invite_code_copy', 'invite_code_manage_new', '复制邀请码', 'system:invite-code:copy', '行操作', 70, '复制邀请码权限'),
        ('button_permission_resource_read', 'permission_manage_new', '资源读取', 'system:permission:resource:read', '基础读取', 10, '读取新权限草稿资源权限'),
        ('button_permission_role_auth_read', 'permission_manage_new', '角色授权读取', 'system:permission:role-auth:read', '基础读取', 20, '读取角色授权与用户角色权限'),
        ('button_permission_manage', 'permission_manage_new', '权限工作台入口', 'system:permission:manage', '基础读取', 30, '新权限工作台入口权限'),
        ('button_permission_resource_save', 'permission_manage_new', '资源保存', 'system:permission:resource:save', '资源工作台', 40, '保存新权限草稿资源权限'),
        ('button_permission_role_create', 'permission_manage_new', '角色创建', 'system:permission:role:create', '角色', 50, '创建角色权限'),
        ('button_permission_role_update', 'permission_manage_new', '角色编辑', 'system:permission:role:update', '角色', 60, '编辑角色权限'),
        ('button_permission_role_delete', 'permission_manage_new', '角色删除', 'system:permission:role:delete', '角色', 70, '删除角色权限'),
        ('button_permission_role_copy', 'permission_manage_new', '角色复制', 'system:permission:role:copy', '角色', 80, '复制角色权限'),
        ('button_permission_role_auth_save', 'permission_manage_new', '角色授权保存', 'system:permission:role-auth:save', '角色授权', 90, '保存角色授权草稿权限'),
        ('button_permission_user_assign', 'permission_manage_new', '用户分配角色', 'system:permission:user:assign', '用户角色', 100, '用户分配角色权限'),
        ('button_permission_role_preview', 'permission_manage_new', '角色预览', 'system:permission:role:preview', '发布', 110, '预览角色权限'),
        ('button_permission_publish_validate', 'permission_manage_new', '发布校验', 'system:permission:publish:validate', '发布', 120, '发布前校验权限'),
        ('button_permission_publish', 'permission_manage_new', '执行发布', 'system:permission:publish', '发布', 130, '执行发布权限'),
        ('button_c5_sniping_task_create', 'page_c5_sniping_task_v2', '新增扫货任务', 'c5:sniping-task:create', '工具栏', 20, 'C5扫货2.0新增任务权限'),
        ('button_c5_sniping_task_update', 'page_c5_sniping_task_v2', '编辑扫货任务', 'c5:sniping-task:update', '行操作', 30, 'C5扫货2.0编辑任务权限'),
        ('button_c5_sniping_task_enable', 'page_c5_sniping_task_v2', '开启扫货任务', 'c5:sniping-task:enable', '行操作', 40, 'C5扫货2.0开启任务权限'),
        ('button_c5_sniping_task_disable', 'page_c5_sniping_task_v2', '停止扫货任务', 'c5:sniping-task:disable', '行操作', 50, 'C5扫货2.0停止任务权限'),
        ('button_c5_sniping_task_delete', 'page_c5_sniping_task_v2', '删除扫货任务', 'c5:sniping-task:delete', '行操作', 60, 'C5扫货2.0删除任务权限'),
        ('button_c5_sniping_task_detail', 'page_c5_sniping_task_v2', '任务记录详情', 'c5:sniping-task:detail', '详情', 70, 'C5扫货2.0任务详情类记录入口权限'),
        ('button_c5_sniping_account_create', 'page_c5_sniping_account_config', '新增扫货账号', 'c5:sniping-account:create', '工具栏', 20, '新增 C5 扫货账号权限'),
        ('button_c5_sniping_account_update', 'page_c5_sniping_account_config', '编辑扫货账号', 'c5:sniping-account:update', '行操作', 30, '编辑 C5 扫货账号权限'),
        ('button_c5_sniping_account_delete', 'page_c5_sniping_account_config', '删除扫货账号', 'c5:sniping-account:delete', '行操作', 40, '删除 C5 扫货账号权限'),
        ('button_c5_sniping_account_detail', 'page_c5_sniping_account_config', '扫货账号详情', 'c5:sniping-account:detail', '详情', 50, 'C5 扫货账号详情权限'),
        ('button_unbox_record_create', 'page_unbox_record', '新建开箱批次', 'unbox:record:create', '批次', 20, '新建开箱记录批次权限'),
        ('button_unbox_record_update', 'page_unbox_record', '编辑开箱批次', 'unbox:record:update', '批次', 30, '编辑开箱记录批次权限'),
        ('button_unbox_record_delete', 'page_unbox_record', '删除开箱批次', 'unbox:record:delete', '批次', 40, '删除开箱记录批次权限'),
        ('button_unbox_record_ocr', 'page_unbox_record', 'OCR 识别', 'unbox:record:ocr', '明细', 50, '开箱记录 OCR 识别权限'),
        ('button_unbox_record_query_c5', 'page_unbox_record', 'C5 查询', 'unbox:record:query-c5', '明细', 60, '开箱记录 C5 挂单查询权限'),
        ('button_unbox_record_detail_add', 'page_unbox_record', '新增明细', 'unbox:record:detail:add', '明细', 70, '开箱记录新增明细权限'),
        ('button_unbox_record_detail_delete', 'page_unbox_record', '删除明细', 'unbox:record:detail:delete', '明细', 80, '开箱记录删除明细权限'),
        ('button_unbox_record_apply_price', 'page_unbox_record', '应用价格', 'unbox:record:apply-price', '明细', 90, '开箱记录应用价格权限'),
        ('button_unbox_record_apply_defaults', 'page_unbox_record', '应用默认值', 'unbox:record:apply-defaults', '明细', 100, '开箱记录应用默认值权限')
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
       and parent.del_flag = 0
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
), target_draft_resource as (
    select rd.id
    from public.resource_draft rd
    where rd.del_flag = 0
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
    tdr.id,
    'flyway',
    now(),
    'flyway',
    now(),
    0
from admin_role ar
cross join target_draft_resource tdr
where not exists (
    select 1
    from public.role_resource_draft rrd
    where rrd.role_id = ar.role_id
      and rrd.resource_id = tdr.id
);

with admin_role as (
    select role_id
    from public.sys_role
    where role_key = 'admin'
      and del_flag = 0
    order by role_id
    limit 1
), target_published_resource as (
    select rp.id
    from public.resource_published rp
    where rp.del_flag = 0
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
    tpr.id,
    'flyway',
    now(),
    'flyway',
    now(),
    0
from admin_role ar
cross join target_published_resource tpr
where not exists (
    select 1
    from public.role_resource_published rrp
    where rrp.role_id = ar.role_id
      and rrp.resource_id = tpr.id
);

commit;

-- ============================================================
-- 执行后复核（可选）
-- ============================================================
-- select resource_key, parent_resource_id, title, permission_code, button_group, status, del_flag
-- from public.resource_published
-- where resource_type = 'BUTTON'
--   and permission_code in (
--     'task:scan:start', 'task:scan:stop', 'task:scan:create', 'system:goods:sync', 'order:c5:sync',
--     'system:invite-code:create', 'system:invite-code:batch-create', 'system:invite-code:update',
--     'system:invite-code:enable', 'system:invite-code:disable', 'system:invite-code:copy',
--     'system:permission:resource:read', 'system:permission:role-auth:read', 'system:permission:manage',
--     'system:permission:resource:save', 'system:permission:role:create', 'system:permission:role:update',
--     'system:permission:role:delete', 'system:permission:role:copy', 'system:permission:role-auth:save',
--     'system:permission:user:assign', 'system:permission:role:preview', 'system:permission:publish:validate',
--     'system:permission:publish', 'c5:sniping-task:create', 'c5:sniping-task:update',
--     'c5:sniping-task:enable', 'c5:sniping-task:disable', 'c5:sniping-task:delete', 'c5:sniping-task:detail',
--     'c5:sniping-account:create', 'c5:sniping-account:update', 'c5:sniping-account:delete',
--     'c5:sniping-account:detail', 'unbox:record:create', 'unbox:record:update', 'unbox:record:delete',
--     'unbox:record:ocr', 'unbox:record:query-c5', 'unbox:record:detail:add',
--     'unbox:record:detail:delete', 'unbox:record:apply-price', 'unbox:record:apply-defaults'
--   )
-- order by parent_resource_id, sort_order;
