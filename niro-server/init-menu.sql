-- ============================================
-- 动态路由系统重构 - 数据库初始化脚本
-- 大厂规范：统一使用相对路径，组件名明确映射
-- ============================================

-- 1. 清空现有菜单数据（注意：如果有关联数据请先备份）
-- TRUNCATE TABLE sys_role_menu CASCADE;
-- TRUNCATE TABLE sys_menu CASCADE;

-- 2. 删除现有菜单（软删除）
UPDATE sys_menu SET del_flag = 1 WHERE del_flag = 0;

-- 3. 插入标准菜单数据
-- menu_type: M=目录, C=菜单, F=按钮
-- component_path: 前端组件映射名

-- 一级菜单（目录）
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component_path, menu_type, icon, visible, status, perms, is_cache, breadcrumb) VALUES
('首页', 0, 1, 'dashboard', 'dashboard', 'C', 'home', 0, 1, 'system:dashboard:view', 0, 1),
('扫货管理', 0, 2, 'task', '', 'M', 'server', 0, 1, 'system:task:view', 1, 1),
('系统管理', 0, 3, 'system', '', 'M', 'setting', 0, 1, 'system:manage:view', 1, 1);

-- 扫货管理二级菜单（目录）
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component_path, menu_type, icon, visible, status, perms, is_cache, breadcrumb) VALUES
('任务管理', (SELECT menu_id FROM sys_menu WHERE menu_name = '扫货管理' AND del_flag = 0), 1, 'manager', '', 'M', 'server', 0, 1, 'system:task:manager', 1, 1);

-- 任务管理三级菜单（页面）
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component_path, menu_type, icon, visible, status, perms, is_cache, breadcrumb) VALUES
('BUFF平台', (SELECT menu_id FROM sys_menu WHERE menu_name = '任务管理' AND parent_id = (SELECT menu_id FROM sys_menu WHERE menu_name = '扫货管理' AND del_flag = 0) AND del_flag = 0), 1, 'buff', 'buff', 'C', 'server', 0, 1, 'system:task:buff:view', 1, 1),
('C5平台', (SELECT menu_id FROM sys_menu WHERE menu_name = '任务管理' AND parent_id = (SELECT menu_id FROM sys_menu WHERE menu_name = '扫货管理' AND del_flag = 0) AND del_flag = 0), 2, 'c5', 'c5', 'C', 'server', 0, 1, 'system:task:c5:view', 1, 1);

-- 扫货管理 - 订单记录（页面）
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component_path, menu_type, icon, visible, status, perms, is_cache, breadcrumb) VALUES
('订单记录', (SELECT menu_id FROM sys_menu WHERE menu_name = '扫货管理' AND del_flag = 0), 2, 'record', 'record', 'C', 'history', 0, 1, 'system:task:record:view', 1, 1);

-- 系统管理二级菜单（页面）
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component_path, menu_type, icon, visible, status, perms, is_cache, breadcrumb) VALUES
('商品管理', (SELECT menu_id FROM sys_menu WHERE menu_name = '系统管理' AND del_flag = 0), 1, 'goods', 'goods', 'C', 'shop', 0, 1, 'system:goods:view', 1, 1),
('印花管理', (SELECT menu_id FROM sys_menu WHERE menu_name = '系统管理' AND del_flag = 0), 2, 'sticker', 'sticker', 'C', 'file', 0, 1, 'system:sticker:view', 1, 1),
('系统日志', (SELECT menu_id FROM sys_menu WHERE menu_name = '系统管理' AND del_flag = 0), 3, 'logs', 'logs', 'C', 'file', 0, 1, 'system:logs:view', 1, 1),
('账号管理', (SELECT menu_id FROM sys_menu WHERE menu_name = '系统管理' AND del_flag = 0), 4, 'account', 'account', 'C', 'setting', 0, 1, 'system:account:view', 1, 1);

-- 4. 给 admin 角色分配所有菜单（先删除旧关联）
DELETE FROM sys_role_menu WHERE role_id = 1;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu WHERE del_flag = 0;

-- 5. 验证结果
SELECT 
    m.menu_id,
    m.menu_name,
    m.parent_id,
    m.order_num,
    m.path,
    m.component_path,
    m.menu_type,
    m.icon,
    m.perms,
    p.menu_name as parent_name
FROM sys_menu m
LEFT JOIN sys_menu p ON m.parent_id = p.menu_id
WHERE m.del_flag = 0
ORDER BY m.parent_id, m.order_num;
