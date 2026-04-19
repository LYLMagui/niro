insert into public.sys_user (
  id,
  username,
  password,
  nickname,
  email,
  avatar,
  status,
  create_time,
  update_time,
  is_delete
)
overriding system value
values
  (1, 'admin', '$2a$10$r9orIJY0zfRJfXunL1H2Lun.nssnyvL5uzaK5z3LxKliNhIDKKXky', '_yL6qKunj', '', '', 1, current_timestamp, current_timestamp, 0),
  (2, 'liyl', '$2a$10$.DjzjAFCrpFgqJC6cnMvYevuslnqhaySwDeK5olH4iXICuPYZGCvG', '_Ra7YtwrS', '', '', 1, current_timestamp, current_timestamp, 0),
  (3, 'senlan', '$2a$10$6.oVCxN3m8yioVYPqCpLV.P61yoDoo3hcXsva7XRyC3r4Q59KTSYW', '_3tkxChJS', '', '', 1, current_timestamp, current_timestamp, 0)
on conflict (id) do update set
  username = excluded.username,
  password = excluded.password,
  nickname = excluded.nickname,
  email = excluded.email,
  avatar = excluded.avatar,
  status = excluded.status,
  update_time = current_timestamp,
  is_delete = excluded.is_delete;

insert into public.sys_role (
  role_id,
  role_name,
  role_key,
  role_sort,
  data_scope,
  status,
  del_flag,
  create_by,
  create_time,
  update_by,
  update_time,
  remark
)
overriding system value
values
  (1, '超级管理员', 'admin', 1, '1', 1, 0, 'admin', current_timestamp, '', null, '系统最高权限角色'),
  (2, '普通用户', 'user', 2, '2', 1, 0, 'admin', current_timestamp, '', null, '普通用户角色')
on conflict (role_id) do update set
  role_name = excluded.role_name,
  role_key = excluded.role_key,
  role_sort = excluded.role_sort,
  data_scope = excluded.data_scope,
  status = excluded.status,
  del_flag = excluded.del_flag,
  update_by = excluded.update_by,
  update_time = current_timestamp,
  remark = excluded.remark;

insert into public.sys_menu (
  id,
  parent_id,
  title,
  name,
  path,
  component,
  icon,
  sort_order,
  type,
  permission,
  hidden,
  keep_alive,
  redirect,
  del_flag,
  create_time,
  update_time,
  status
)
overriding system value
values
  (1, 0, '首页', 'Dashboard', 'dashboard', 'dashboard', 'home', 1, 1, 'system:dashboard:view', false, true, null, 0, current_timestamp, current_timestamp, 1),
  (2, 0, '扫货管理', 'Task', 'task', 'ParentView', 'server', 2, 0, null, false, true, '/task/manager/buff', 0, current_timestamp, current_timestamp, 1),
  (3, 2, '任务管理', 'TaskManager', 'manager', 'ParentView', 'app', 1, 0, null, false, true, '/task/manager/buff', 0, current_timestamp, current_timestamp, 1),
  (4, 3, 'BUFF平台', 'BuffTask', 'buff', 'buff', 'server', 1, 1, 'task:buff:list', false, true, null, 0, current_timestamp, current_timestamp, 1),
  (5, 3, 'C5平台', 'C5Task', 'c5', 'c5', 'server', 2, 1, 'task:c5:list', false, true, null, 0, current_timestamp, current_timestamp, 1),
  (6, 2, '订单记录', 'OrderRecord', 'record', 'record', 'history', 2, 1, 'task:record:list', false, true, null, 0, current_timestamp, current_timestamp, 1),
  (7, 0, '系统管理', 'System', 'system', 'ParentView', 'setting', 3, 0, null, false, true, '/system/account', 0, current_timestamp, current_timestamp, 1),
  (8, 7, '商品管理', 'GoodsList', 'goods', 'goods', 'shop', 1, 1, 'system:goods:list', false, true, null, 0, current_timestamp, current_timestamp, 1),
  (9, 7, '系统日志', 'SystemLogs', 'logs', 'logs', 'list', 3, 1, 'system:logs:list', false, true, null, 0, current_timestamp, current_timestamp, 1),
  (10, 7, '账号管理', 'AccountList', 'account', 'account', 'user', 4, 1, 'system:account:list', false, true, null, 0, current_timestamp, current_timestamp, 1),
  (11, 2, '订单统计', 'OrderStatistics', 'inventory', 'inventory', 'dashboard', 3, 1, 'task:inventory:view', false, true, null, 0, current_timestamp, current_timestamp, 1),
  (12, 2, '利润统计', 'ProfitStatistics', 'profit', 'profit', 'chart', 4, 1, 'system:profit:view', false, true, null, 0, current_timestamp, current_timestamp, 1),
  (14, 2, '开箱记录', 'UnboxRecord', 'unboxrecord', 'unboxrecord', 'goods', 5, 1, 'system:task:unboxrecord:view', false, true, '', 0, current_timestamp, current_timestamp, 1),
  (15, 7, '权限管理', 'PermissionManage', 'permission', 'permission', 'secure', 5, 1, 'system:permission:manage', false, true, null, 0, current_timestamp, current_timestamp, 1)
on conflict (id) do update set
  parent_id = excluded.parent_id,
  title = excluded.title,
  name = excluded.name,
  path = excluded.path,
  component = excluded.component,
  icon = excluded.icon,
  sort_order = excluded.sort_order,
  type = excluded.type,
  permission = excluded.permission,
  hidden = excluded.hidden,
  keep_alive = excluded.keep_alive,
  redirect = excluded.redirect,
  del_flag = excluded.del_flag,
  update_time = current_timestamp,
  status = excluded.status;

delete from public.sys_user_role where user_id in (1, 2, 3);
insert into public.sys_user_role (user_id, role_id)
values
  (1, 1),
  (2, 2),
  (3, 2)
on conflict do nothing;

delete from public.sys_role_menu where role_id in (1, 2);
insert into public.sys_role_menu (role_id, menu_id)
values
  (1, 1),
  (1, 2),
  (1, 3),
  (1, 4),
  (1, 5),
  (1, 6),
  (1, 7),
  (1, 8),
  (1, 9),
  (1, 10),
  (1, 11),
  (1, 12),
  (1, 14),
  (1, 15),
  (2, 1),
  (2, 2),
  (2, 3),
  (2, 4),
  (2, 5)
on conflict do nothing;

select setval(pg_get_serial_sequence('public.sys_user', 'id'), coalesce((select max(id) from public.sys_user), 1), true);
select setval(pg_get_serial_sequence('public.sys_role', 'role_id'), coalesce((select max(role_id) from public.sys_role), 1), true);
select setval(pg_get_serial_sequence('public.sys_menu', 'id'), coalesce((select max(id) from public.sys_menu), 1), true);
