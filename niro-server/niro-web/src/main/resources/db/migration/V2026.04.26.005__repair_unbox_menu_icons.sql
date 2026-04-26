-- ============================================================
-- 修复开箱菜单图标（PostgreSQL · 追加式 migration）
-- 日期: 2026-04-26
-- 目标:
-- 1. 为旧菜单 sys_menu 中的开箱菜单补充 gift 图标
-- 2. 为新权限草稿态和发布态资源中的开箱目录、开箱记录页面补充 gift 图标
-- 幂等策略:
-- - 按稳定唯一键 name / resource_key 定位记录
-- - update 重复执行结果一致，不依赖自增 id
-- 回滚思路:
-- - 如需回滚，可新增 migration 将对应 icon 置空或恢复为历史值
-- ============================================================

begin;

do $$
begin
    if to_regclass('public.sys_menu') is not null then
        update public.sys_menu
        set icon = 'gift',
            update_time = now()
        where name = 'UnboxRecord'
          and del_flag = 0
          and coalesce(icon, '') <> 'gift';
    end if;
end $$;

update public.resource_draft
set icon = 'gift',
    updated_by = 'flyway',
    updated_at = now()
where resource_key in ('nav_unbox', 'page_unbox_record')
  and del_flag = 0
  and coalesce(icon, '') <> 'gift';

update public.resource_published
set icon = 'gift',
    updated_by = 'flyway',
    updated_at = now()
where resource_key in ('nav_unbox', 'page_unbox_record')
  and del_flag = 0
  and coalesce(icon, '') <> 'gift';

commit;

-- 复核 SQL：
-- select to_regclass('public.sys_menu');
-- select resource_key, icon from public.resource_published where resource_key in ('nav_unbox', 'page_unbox_record') and del_flag = 0;
