-- ============================================================
-- 新权限按钮父资源完整性校验（PostgreSQL · 追加式 migration）
-- 日期: 2026-04-25
-- 目标:
-- 1. 校验新权限按钮依赖的页面父资源在 draft / published 中均已存在
-- 2. 在后续环境执行旧权限表删除前，提前阻断不完整的数据基线
-- 幂等策略:
-- - 仅执行校验，不写入业务数据
-- - 父资源齐全时可重复执行且结果一致
-- 回滚思路:
-- - 无状态校验脚本，无需回滚
-- ============================================================

begin;

do $$
begin
    if exists (
        with button_parent_seed(parent_resource_key) as (
            values
                ('page_task_list'),
                ('page_order_record'),
                ('page_inventory_board'),
                ('page_c5_sniping_task_v2'),
                ('logs_new'),
                ('page_settings'),
                ('page_goods_list'),
                ('page_sticker_list'),
                ('invite_code_manage_new'),
                ('permission_manage_new')
        )
        select 1
        from button_parent_seed bs
        where not exists (
            select 1
            from public.resource_draft parent
            where parent.resource_key = bs.parent_resource_key
              and parent.del_flag = 0
        )
    ) then
        raise exception '迁移终止：resource_draft 缺少按钮父资源，请先补齐新权限页面基线';
    end if;

    if exists (
        with button_parent_seed(parent_resource_key) as (
            values
                ('page_task_list'),
                ('page_order_record'),
                ('page_inventory_board'),
                ('page_c5_sniping_task_v2'),
                ('logs_new'),
                ('page_settings'),
                ('page_goods_list'),
                ('page_sticker_list'),
                ('invite_code_manage_new'),
                ('permission_manage_new')
        )
        select 1
        from button_parent_seed bs
        where not exists (
            select 1
            from public.resource_published parent
            where parent.resource_key = bs.parent_resource_key
              and parent.del_flag = 0
        )
    ) then
        raise exception '迁移终止：resource_published 缺少按钮父资源，请先补齐并发布新权限页面基线';
    end if;
end $$;

commit;
