-- ============================================================
-- 菜单重构 V1（PostgreSQL · 追加式 migration）
-- 日期: 2026-04-20
-- 依据: docs/prototypes/menu-redesign-v1.html
--
-- 目标（页面不改，只调整菜单层级/名称/路径）:
-- 1. 顶级: 工作台 / 扫货 / 订单 / 开箱 / 商品库 / 系统
-- 2. 订单目录: 订单记录 / 订单统计
-- 3. 系统目录: 账号 / 权限 / 日志
-- 4. 废弃（软删除）: 任务管理 / BUFF 平台 / C5 平台 / 利润统计
--
-- 幂等说明:
-- - 按 sys_menu.name 定位既有菜单
-- - "订单"目录通过 not exists 保护，不会重复插入
-- - admin 角色授权通过 not exists 保护，不会重复
-- ============================================================

begin;

-- 1. 顶级菜单更名 / 降级 / 提升 --------------------------------

-- 1.1 工作台（原"首页"）
update public.sys_menu
set title = '工作台',
    sort_order = 1,
    update_time = now()
where name = 'Dashboard'
  and del_flag = 0;

-- 1.2 扫货（原"扫货管理"目录 → 菜单，直接指向 TaskList，与现状 PlatformEnum.C5 保持一致）
update public.sys_menu
set title = '扫货',
    path = 'scan',
    type = 1,
    component = 'c5',
    redirect = null,
    sort_order = 2,
    update_time = now()
where name = 'Task'
  and del_flag = 0;

-- 1.3 开箱（原"开箱记录"，提升为顶级）
update public.sys_menu
set title = '开箱',
    path = 'unbox',
    parent_id = 0,
    sort_order = 4,
    update_time = now()
where name = 'UnboxRecord'
  and del_flag = 0;

-- 1.4 商品库（原"商品管理"，提升为顶级）
update public.sys_menu
set title = '商品库',
    parent_id = 0,
    sort_order = 5,
    update_time = now()
where name = 'GoodsList'
  and del_flag = 0;

-- 1.5 系统（原"系统管理"）
update public.sys_menu
set title = '系统',
    sort_order = 6,
    update_time = now()
where name = 'System'
  and del_flag = 0;


-- 2. 系统目录下的子菜单改名 + 排序 -----------------------------

update public.sys_menu
set title = '账号',
    sort_order = 1,
    update_time = now()
where name = 'AccountList'
  and del_flag = 0;

update public.sys_menu
set title = '权限',
    sort_order = 2,
    update_time = now()
where name = 'PermissionManage'
  and del_flag = 0;

update public.sys_menu
set title = '日志',
    sort_order = 3,
    update_time = now()
where name = 'SystemLogs'
  and del_flag = 0;


-- 3. 新增"订单"顶级目录（幂等）---------------------------------

insert into public.sys_menu (
    parent_id, title, name, path, component, icon,
    sort_order, type, permission, status, hidden, keep_alive, redirect, del_flag
)
select 0, '订单', 'Order', 'order', 'ParentView', 'history',
       3, 0, null, 1, false, true, '/order/record', 0
where not exists (
    select 1
    from public.sys_menu
    where name = 'Order'
      and del_flag = 0
);


-- 4. 订单子菜单挂到"订单"目录下 --------------------------------

update public.sys_menu
set title = '订单记录',
    parent_id = (
        select id
        from public.sys_menu
        where name = 'Order'
          and del_flag = 0
          and type = 0
        order by id
        limit 1
    ),
    sort_order = 1,
    update_time = now()
where name = 'OrderRecord'
  and del_flag = 0;

update public.sys_menu
set title = '订单统计',
    path = 'stat',
    parent_id = (
        select id
        from public.sys_menu
        where name = 'Order'
          and del_flag = 0
          and type = 0
        order by id
        limit 1
    ),
    sort_order = 2,
    update_time = now()
where name = 'OrderStatistics'
  and del_flag = 0;


-- 5. 软删除废弃菜单 --------------------------------------------
-- 任务管理 / BUFF 平台 / C5 平台（功能已合并到 id=Task 的"扫货"单菜单）
-- 利润统计（V1 已合并到订单统计，ProfitStats.vue 文件保留以备后用）

update public.sys_menu
set del_flag = 1,
    update_time = now()
where name in ('TaskManager', 'BuffTask', 'C5Task', 'ProfitStatistics')
  and del_flag = 0;


-- 6. admin 角色补发新"订单"目录的授权 --------------------------

with target_menu as (
    select id
    from public.sys_menu
    where name = 'Order'
      and del_flag = 0
    order by id
    limit 1
),
admin_role as (
    select role_id
    from public.sys_role
    where del_flag = 0
      and role_key = 'admin'
    order by role_id
    limit 1
)
insert into public.sys_role_menu (role_id, menu_id)
select ar.role_id, tm.id
from admin_role ar
cross join target_menu tm
where not exists (
    select 1
    from public.sys_role_menu rm
    where rm.role_id = ar.role_id
      and rm.menu_id = tm.id
);


-- 7. 清理已软删除菜单的角色授权 --------------------------------
-- 前端按 del_flag=0 过滤，但顺手清理避免 sys_role_menu 残留脏数据

delete from public.sys_role_menu rm
using public.sys_menu m
where rm.menu_id = m.id
  and m.del_flag = 1
  and m.name in ('TaskManager', 'BuffTask', 'C5Task', 'ProfitStatistics');


commit;


-- ============================================================
-- 执行后复核（可选）
-- ============================================================
-- select id, parent_id, title, name, path, component, type, sort_order, hidden, del_flag
-- from public.sys_menu
-- where del_flag = 0
-- order by parent_id, sort_order, id;
--
-- select r.role_key, m.id as menu_id, m.title, m.path, m.parent_id
-- from public.sys_role_menu rm
-- join public.sys_role r on r.role_id = rm.role_id and r.del_flag = 0
-- join public.sys_menu m on m.id = rm.menu_id and m.del_flag = 0
-- order by r.role_key, m.parent_id, m.sort_order;
