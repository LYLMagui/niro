<template>
  <t-card :bordered="false" class="shadow-sm">
    <template #title>
      <div class="flex items-center gap-2">
        <t-icon name="secured" class="text-blue-600" />
        <span class="text-lg font-bold text-gray-800">权限管理</span>
      </div>
    </template>

    <template #actions>
      <t-button theme="primary" variant="outline" size="small" :loading="refreshing" @click="refreshAll">
        刷新数据
      </t-button>
    </template>

    <t-alert
      theme="info"
      title="说明"
      description="本页用于角色、菜单/按钮、授权和批量分配统一管理。权限变更后会统一提醒相关用户刷新页面。"
      close
    />

    <t-tabs v-model="activeTab" class="mt-4">
      <t-tab-panel value="roles" label="角色管理">
        <div class="mb-4 flex flex-wrap items-end gap-3">
          <t-input
            v-model="roleFilter.keyword"
            class="w-[260px]"
            clearable
            placeholder="按角色名/编码搜索"
            @enter="queryRoles"
          />
          <t-select v-model="roleFilter.status" clearable class="w-[160px]" placeholder="角色状态">
            <t-option :value="1" label="正常" />
            <t-option :value="0" label="停用" />
          </t-select>
          <t-button theme="primary" :loading="rolesLoading" @click="queryRoles">查询</t-button>
          <t-button variant="outline" @click="resetRoleFilter">重置</t-button>
          <t-button theme="primary" class="ml-auto" @click="openCreateRoleDialog">新增角色</t-button>
        </div>

        <t-table
          :data="roles"
          :columns="roleColumns"
          row-key="roleId"
          :loading="rolesLoading"
          size="small"
          :bordered="false"
        >
          <template #status="{ row }">
            <t-tag :theme="row.status === 1 ? 'success' : 'warning'" variant="light">
              {{ row.status === 1 ? "正常" : "停用" }}
            </t-tag>
          </template>

          <template #operation="{ row }">
            <div class="flex items-center justify-center gap-2">
              <t-button size="small" theme="primary" variant="outline" @click="openEditRoleDialog(row)">
                编辑
              </t-button>
              <t-popconfirm content="确定删除该角色吗？" @confirm="deleteRole(row)">
                <t-button size="small" theme="danger" variant="outline">删除</t-button>
              </t-popconfirm>
            </div>
          </template>
        </t-table>
      </t-tab-panel>

      <t-tab-panel value="menus" label="菜单/按钮管理">
        <div class="mb-4 flex flex-wrap items-end gap-3">
          <t-input
            v-model="menuKeyword"
            class="w-[260px]"
            clearable
            placeholder="按标题/权限码搜索"
          />
          <t-select v-model="menuFilter.type" clearable class="w-[160px]" placeholder="菜单类型">
            <t-option :value="0" label="目录" />
            <t-option :value="1" label="菜单" />
            <t-option :value="2" label="按钮" />
          </t-select>
          <t-select v-model="menuFilter.status" clearable class="w-[160px]" placeholder="菜单状态">
            <t-option :value="1" label="正常" />
            <t-option :value="0" label="停用" />
          </t-select>
          <t-button theme="primary" :loading="menusLoading" @click="queryMenus">查询</t-button>
          <t-button variant="outline" @click="resetMenuFilter">重置</t-button>
          <t-button theme="primary" class="ml-auto" @click="openCreateMenuDialog">新增菜单/按钮</t-button>
        </div>

        <t-table
          :data="filteredMenus"
          :columns="menuColumns"
          row-key="id"
          :loading="menusLoading"
          size="small"
          :bordered="false"
        >
          <template #type="{ row }">
            <t-tag variant="light-outline">{{ menuTypeLabel(row.type) }}</t-tag>
          </template>

          <template #status="{ row }">
            <t-tag :theme="row.status === 1 ? 'success' : 'warning'" variant="light">
              {{ row.status === 1 ? "正常" : "停用" }}
            </t-tag>
          </template>

          <template #parent="{ row }">
            <span>{{ menuParentLabel(row.parentId) }}</span>
          </template>

          <template #permission="{ row }">
            <span class="font-mono text-xs">{{ row.permission || "-" }}</span>
          </template>

          <template #operation="{ row }">
            <div class="flex items-center justify-center gap-2">
              <t-button size="small" theme="primary" variant="outline" @click="openEditMenuDialog(row)">
                编辑
              </t-button>
              <t-popconfirm content="确定删除该菜单吗？" @confirm="deleteMenu(row)">
                <t-button size="small" theme="danger" variant="outline">删除</t-button>
              </t-popconfirm>
            </div>
          </template>
        </t-table>
      </t-tab-panel>

      <t-tab-panel value="authorize" label="角色授权">
        <div class="mb-4 flex flex-wrap items-center gap-3">
          <t-select
            v-model="selectedRoleId"
            clearable
            class="w-[320px]"
            placeholder="选择角色后配置授权"
            @change="onRoleChange"
          >
            <t-option
              v-for="role in activeRoles"
              :key="role.roleId"
              :value="role.roleId"
              :label="`${role.roleName} (${role.roleKey})`"
            />
          </t-select>
          <t-button
            theme="primary"
            :disabled="selectedRoleId === undefined"
            :loading="savingRoleMenus"
            @click="saveRoleMenus"
          >
            保存授权
          </t-button>
        </div>

        <div class="rounded border border-gray-200 p-3">
          <t-tree
            v-model:value="checkedMenuIds"
            :data="menuTreeData"
            checkable
            hover
            expand-all
            transition
          />
        </div>
      </t-tab-panel>

      <t-tab-panel value="batch" label="用户批量分配">
        <div class="grid grid-cols-1 gap-4 xl:grid-cols-2">
          <div>
            <h3 class="mb-2 text-sm font-semibold text-gray-700">选择用户</h3>
            <t-select
              v-model="batchUserIds"
              multiple
              clearable
              filterable
              :min-collapsed-num="1"
              placeholder="可多选用户"
            >
              <t-option
                v-for="user in users"
                :key="user.id"
                :value="user.id"
                :label="`${user.username}${user.nickname ? ` (${user.nickname})` : ''}`"
              />
            </t-select>
          </div>

          <div>
            <h3 class="mb-2 text-sm font-semibold text-gray-700">追加角色</h3>
            <t-select
              v-model="batchRoleIds"
              multiple
              clearable
              filterable
              :min-collapsed-num="1"
              placeholder="可多选角色"
            >
              <t-option
                v-for="role in activeRoles"
                :key="role.roleId"
                :value="role.roleId"
                :label="`${role.roleName} (${role.roleKey})`"
              />
            </t-select>
          </div>
        </div>

        <div class="mt-4 flex justify-end">
          <t-button theme="primary" :loading="batchAppending" @click="batchAppendRoles">
            批量追加角色
          </t-button>
        </div>

        <div class="mt-4">
          <t-table
            :data="users"
            :columns="userColumns"
            row-key="id"
            :loading="usersLoading"
            size="small"
            :bordered="false"
          >
            <template #status="{ row }">
              <t-tag :theme="row.status === 1 ? 'success' : 'warning'" variant="light">
                {{ row.status === 1 ? "正常" : "禁用" }}
              </t-tag>
            </template>

            <template #roles="{ row }">
              <div class="flex flex-wrap gap-1">
                <t-tag
                  v-for="roleId in row.roleIds"
                  :key="`${row.id}-${roleId}`"
                  size="small"
                  variant="light-outline"
                >
                  {{ roleLabelMap.get(roleId) || `角色#${roleId}` }}
                </t-tag>
                <span v-if="!row.roleIds?.length" class="text-xs text-gray-400">未分配角色</span>
              </div>
            </template>
          </t-table>
        </div>
      </t-tab-panel>
    </t-tabs>
  </t-card>

  <t-dialog
    v-model:visible="roleDialogVisible"
    :header="roleDialogMode === 'create' ? '新增角色' : '编辑角色'"
    width="560px"
    :footer="false"
  >
    <t-form
      ref="roleFormRef"
      :data="roleFormData"
      :rules="roleRules"
      label-align="top"
      class="p-1"
      @submit="onRoleSubmit"
    >
      <div class="grid grid-cols-2 gap-4">
        <t-form-item label="角色名称" name="roleName">
          <t-input v-model="roleFormData.roleName" placeholder="请输入角色名称" />
        </t-form-item>
        <t-form-item label="角色编码" name="roleKey">
          <t-input v-model="roleFormData.roleKey" placeholder="如：admin / operator" />
        </t-form-item>
      </div>

      <div class="grid grid-cols-2 gap-4">
        <t-form-item label="角色排序" name="roleSort">
          <t-input-number v-model="roleFormData.roleSort" class="w-full" :min="0" theme="column" />
        </t-form-item>
        <t-form-item label="状态" name="status">
          <t-select v-model="roleFormData.status">
            <t-option :value="1" label="正常" />
            <t-option :value="0" label="停用" />
          </t-select>
        </t-form-item>
      </div>

      <t-form-item label="备注" name="remark">
        <t-textarea v-model="roleFormData.remark" :autosize="{ minRows: 2, maxRows: 4 }" />
      </t-form-item>

      <div class="mt-6 flex justify-end gap-3">
        <t-button variant="outline" @click="roleDialogVisible = false">取消</t-button>
        <t-button theme="primary" type="submit" :loading="roleSubmitting">保存</t-button>
      </div>
    </t-form>
  </t-dialog>

  <t-dialog
    v-model:visible="menuDialogVisible"
    :header="menuDialogMode === 'create' ? '新增菜单/按钮' : '编辑菜单/按钮'"
    width="680px"
    :footer="false"
  >
    <t-form
      ref="menuFormRef"
      :data="menuFormData"
      :rules="menuRules"
      label-align="top"
      class="p-1"
      @submit="onMenuSubmit"
    >
      <div class="grid grid-cols-3 gap-4">
        <t-form-item label="类型" name="type">
          <t-select v-model="menuFormData.type" @change="onMenuTypeChanged">
            <t-option :value="0" label="目录" />
            <t-option :value="1" label="菜单" />
            <t-option :value="2" label="按钮" />
          </t-select>
        </t-form-item>
        <t-form-item label="状态" name="status">
          <t-select v-model="menuFormData.status">
            <t-option :value="1" label="正常" />
            <t-option :value="0" label="停用" />
          </t-select>
        </t-form-item>
        <t-form-item label="排序" name="sortOrder">
          <t-input-number v-model="menuFormData.sortOrder" class="w-full" :min="0" theme="column" />
        </t-form-item>
      </div>

      <t-form-item label="上级菜单" name="parentId">
        <t-select v-model="menuFormData.parentId" clearable placeholder="不选则为根节点">
          <t-option :value="0" label="根节点" />
          <t-option
            v-for="parent in parentMenuOptions"
            :key="parent.value"
            :value="parent.value"
            :label="parent.label"
          />
        </t-select>
      </t-form-item>

      <div class="grid grid-cols-2 gap-4">
        <t-form-item label="标题" name="title">
          <t-input v-model="menuFormData.title" placeholder="请输入菜单标题" />
        </t-form-item>
        <t-form-item label="权限码" name="permission">
          <t-input
            v-model="menuFormData.permission"
            :disabled="menuFormData.type === 0"
            placeholder="如：system:user:list"
          />
        </t-form-item>
      </div>

      <div class="grid grid-cols-2 gap-4">
        <t-form-item label="路由名称" name="name">
          <t-input v-model="menuFormData.name" placeholder="可选，如：PermissionManage" />
        </t-form-item>
        <t-form-item label="路由路径" name="path">
          <t-input v-model="menuFormData.path" placeholder="可选，如：/system/permission" />
        </t-form-item>
      </div>

      <div class="grid grid-cols-2 gap-4">
        <t-form-item label="组件路径" name="component">
          <t-input v-model="menuFormData.component" placeholder="如：permission / rbac" />
        </t-form-item>
        <t-form-item label="图标" name="icon">
          <t-input v-model="menuFormData.icon" placeholder="可选，如：secured" />
        </t-form-item>
      </div>

      <div class="grid grid-cols-2 gap-4">
        <t-form-item label="重定向" name="redirect">
          <t-input v-model="menuFormData.redirect" placeholder="可选，如：/dashboard" />
        </t-form-item>
        <div class="grid grid-cols-2 gap-4">
          <t-form-item label="隐藏菜单" name="hidden">
            <t-switch v-model="menuFormData.hidden" />
          </t-form-item>
          <t-form-item label="缓存页面" name="keepAlive">
            <t-switch v-model="menuFormData.keepAlive" />
          </t-form-item>
        </div>
      </div>

      <div class="mt-6 flex justify-end gap-3">
        <t-button variant="outline" @click="menuDialogVisible = false">取消</t-button>
        <t-button theme="primary" type="submit" :loading="menuSubmitting">保存</t-button>
      </div>
    </t-form>
  </t-dialog>
</template>

<script setup lang="ts">
import {
  rbacApi,
  type MenuMutationParam,
  type RbacMenu,
  type RbacRole,
  type RbacUser,
  type RoleMutationParam,
} from "@/api/rbac";
import {
  FormRule,
  MessagePlugin,
  type PrimaryTableCol,
  type SubmitContext,
  type TableRowData,
} from "tdesign-vue-next";
import { computed, onMounted, reactive, ref } from "vue";

type MenuTreeNode = {
  label: string;
  value: number;
  children?: MenuTreeNode[];
};

type OptionItem = {
  label: string;
  value: number;
};

type RoleDialogMode = "create" | "edit";
type MenuDialogMode = "create" | "edit";

const PERMISSION_REFRESH_MESSAGE = "权限已更新，请相关用户刷新页面";

const activeTab = ref("roles");
const refreshing = ref(false);

const roles = ref<RbacRole[]>([]);
const menus = ref<RbacMenu[]>([]);
const users = ref<RbacUser[]>([]);

const rolesLoading = ref(false);
const menusLoading = ref(false);
const usersLoading = ref(false);
const savingRoleMenus = ref(false);
const batchAppending = ref(false);

const roleFilter = reactive<{ keyword: string; status: number | undefined }>({
  keyword: "",
  status: undefined,
});
const menuFilter = reactive<{ type: number | undefined; status: number | undefined }>({
  type: undefined,
  status: undefined,
});
const menuKeyword = ref("");

const selectedRoleId = ref<number | undefined>(undefined);
const checkedMenuIds = ref<Array<number | string>>([]);

const batchUserIds = ref<number[]>([]);
const batchRoleIds = ref<number[]>([]);

const roleDialogVisible = ref(false);
const roleDialogMode = ref<RoleDialogMode>("create");
const roleSubmitting = ref(false);
const editingRoleId = ref<number | undefined>(undefined);
const roleFormRef = ref();

const menuDialogVisible = ref(false);
const menuDialogMode = ref<MenuDialogMode>("create");
const menuSubmitting = ref(false);
const editingMenuId = ref<number | undefined>(undefined);
const menuFormRef = ref();

const roleFormData = reactive<RoleMutationParam>({
  roleName: "",
  roleKey: "",
  roleSort: 0,
  status: 1,
  remark: "",
});

const menuFormData = reactive<MenuMutationParam>({
  parentId: 0,
  title: "",
  name: "",
  path: "",
  component: "",
  icon: "",
  sortOrder: 0,
  type: 1,
  permission: "",
  status: 1,
  hidden: false,
  keepAlive: false,
  redirect: "",
});

const roleRules: Record<string, FormRule[]> = {
  roleName: [{ required: true, message: "角色名称不能为空", type: "error", trigger: "blur" }],
  roleKey: [{ required: true, message: "角色编码不能为空", type: "error", trigger: "blur" }],
  roleSort: [{ required: true, message: "角色排序不能为空", type: "error", trigger: "change" }],
  status: [{ required: true, message: "请选择角色状态", type: "error", trigger: "change" }],
};

const menuRules = computed<Record<string, FormRule[]>>(() => {
  const rules: Record<string, FormRule[]> = {
    title: [{ required: true, message: "菜单标题不能为空", type: "error", trigger: "blur" }],
    type: [{ required: true, message: "菜单类型不能为空", type: "error", trigger: "change" }],
    status: [{ required: true, message: "菜单状态不能为空", type: "error", trigger: "change" }],
  };

  if (menuFormData.type !== 0) {
    rules.permission = [
      { required: true, message: "菜单/按钮权限码不能为空", type: "error", trigger: "blur" },
    ];
  }

  if (menuFormData.type === 2) {
    rules.parentId = [
      { required: true, message: "按钮必须选择上级菜单", type: "error", trigger: "change" },
    ];
  }

  return rules;
});

const roleColumns: PrimaryTableCol<TableRowData>[] = [
  { colKey: "roleName", title: "角色名称", minWidth: 160 },
  { colKey: "roleKey", title: "角色编码", minWidth: 140 },
  { colKey: "roleSort", title: "排序", width: 90 },
  { colKey: "status", title: "状态", width: 90, cell: "status" },
  { colKey: "remark", title: "备注", minWidth: 180 },
  { colKey: "operation", title: "操作", width: 170, align: "center", cell: "operation" },
];

const menuColumns: PrimaryTableCol<TableRowData>[] = [
  { colKey: "id", title: "ID", width: 70 },
  { colKey: "title", title: "标题", minWidth: 160 },
  { colKey: "type", title: "类型", width: 90, cell: "type" },
  { colKey: "parent", title: "上级菜单", minWidth: 140, cell: "parent" },
  { colKey: "permission", title: "权限码", minWidth: 220, cell: "permission" },
  { colKey: "status", title: "状态", width: 90, cell: "status" },
  { colKey: "sortOrder", title: "排序", width: 90 },
  { colKey: "operation", title: "操作", width: 170, align: "center", cell: "operation" },
];

const userColumns: PrimaryTableCol<TableRowData>[] = [
  { colKey: "username", title: "用户名", width: 140 },
  { colKey: "nickname", title: "昵称", width: 140 },
  { colKey: "status", title: "状态", width: 90, cell: "status" },
  { colKey: "roles", title: "当前角色", minWidth: 280, cell: "roles" },
];

const activeRoles = computed(() => roles.value.filter((role) => role.status === 1));

const roleLabelMap = computed(() => {
  return new Map<number, string>(
    roles.value.map((role) => [role.roleId, `${role.roleName} (${role.roleKey})`])
  );
});

const menuTitleMap = computed(() => {
  return new Map<number, string>(menus.value.map((menu) => [menu.id, menu.title]));
});

const filteredMenus = computed(() => {
  const keyword = menuKeyword.value.trim().toLowerCase();
  if (!keyword) {
    return menus.value;
  }
  return menus.value.filter((menu) => {
    const title = (menu.title || "").toLowerCase();
    const permission = (menu.permission || "").toLowerCase();
    return title.includes(keyword) || permission.includes(keyword);
  });
});

const menuTreeData = computed<MenuTreeNode[]>(() => {
  const availableMenus = menus.value.filter((menu) => menu.status !== 0);
  if (!availableMenus.length) {
    return [];
  }

  const nodeMap = new Map<number, MenuTreeNode>();
  availableMenus.forEach((menu) => {
    const permissionSuffix = menu.permission ? ` [${menu.permission}]` : "";
    nodeMap.set(menu.id, {
      label: `${menu.title}（${menuTypeLabel(menu.type)}）${permissionSuffix}`,
      value: menu.id,
      children: [],
    });
  });

  const roots: MenuTreeNode[] = [];
  availableMenus.forEach((menu) => {
    const current = nodeMap.get(menu.id);
    if (!current) {
      return;
    }

    if (menu.parentId > 0 && nodeMap.has(menu.parentId)) {
      const parent = nodeMap.get(menu.parentId);
      parent?.children?.push(current);
    } else {
      roots.push(current);
    }
  });

  const normalize = (nodes: MenuTreeNode[]) => {
    nodes.forEach((node) => {
      if (node.children && node.children.length > 0) {
        normalize(node.children);
      } else {
        delete node.children;
      }
    });
  };
  normalize(roots);
  return roots;
});

const parentMenuOptions = computed<OptionItem[]>(() => {
  return menus.value
    .filter((menu) => {
      if (editingMenuId.value !== undefined && menu.id === editingMenuId.value) {
        return false;
      }
      if (menu.status === 0) {
        return false;
      }
      if (menuFormData.type === 2) {
        return menu.type === 1;
      }
      return menu.type !== 2;
    })
    .map((menu) => ({
      value: menu.id,
      label: `${menu.title} (#${menu.id})`,
    }));
});

const notifyPermissionUpdated = () => {
  MessagePlugin.success(PERMISSION_REFRESH_MESSAGE);
};

const menuTypeLabel = (type: number) => {
  if (type === 0) return "目录";
  if (type === 1) return "菜单";
  if (type === 2) return "按钮";
  return "未知";
};

const menuParentLabel = (parentId: number) => {
  if (!parentId || parentId <= 0) {
    return "根节点";
  }
  return menuTitleMap.value.get(parentId) || `菜单#${parentId}`;
};

const resetRoleForm = () => {
  roleFormData.roleName = "";
  roleFormData.roleKey = "";
  roleFormData.roleSort = 0;
  roleFormData.status = 1;
  roleFormData.remark = "";
};

const resetMenuForm = () => {
  menuFormData.parentId = 0;
  menuFormData.title = "";
  menuFormData.name = "";
  menuFormData.path = "";
  menuFormData.component = "";
  menuFormData.icon = "";
  menuFormData.sortOrder = 0;
  menuFormData.type = 1;
  menuFormData.permission = "";
  menuFormData.status = 1;
  menuFormData.hidden = false;
  menuFormData.keepAlive = false;
  menuFormData.redirect = "";
};

const loadRoles = async () => {
  rolesLoading.value = true;
  try {
    roles.value = (await rbacApi.listRoles({
      keyword: roleFilter.keyword || undefined,
      status: roleFilter.status,
    })) || [];

    if (selectedRoleId.value !== undefined) {
      const stillExists = roles.value.some((role) => role.roleId === selectedRoleId.value);
      if (!stillExists) {
        selectedRoleId.value = undefined;
        checkedMenuIds.value = [];
      }
    }
  } finally {
    rolesLoading.value = false;
  }
};

const loadMenus = async () => {
  menusLoading.value = true;
  try {
    menus.value = (await rbacApi.listMenus({
      type: menuFilter.type,
      status: menuFilter.status,
    })) || [];
  } finally {
    menusLoading.value = false;
  }
};

const loadUsers = async () => {
  usersLoading.value = true;
  try {
    users.value = (await rbacApi.listUsers()) || [];
  } finally {
    usersLoading.value = false;
  }
};

const queryRoles = async () => {
  await loadRoles();
};

const queryMenus = async () => {
  await loadMenus();
};

const resetRoleFilter = async () => {
  roleFilter.keyword = "";
  roleFilter.status = undefined;
  await loadRoles();
};

const resetMenuFilter = async () => {
  menuFilter.type = undefined;
  menuFilter.status = undefined;
  menuKeyword.value = "";
  await loadMenus();
};

const refreshAll = async () => {
  refreshing.value = true;
  try {
    await Promise.all([loadRoles(), loadMenus(), loadUsers()]);
    if (selectedRoleId.value !== undefined) {
      await loadRoleMenus(selectedRoleId.value);
    }
  } finally {
    refreshing.value = false;
  }
};

const loadRoleMenus = async (roleId: number) => {
  checkedMenuIds.value = (await rbacApi.getRoleMenuIds(roleId)) || [];
};

const onRoleChange = async (value: unknown) => {
  if (Array.isArray(value)) {
    return;
  }
  if (value === null || value === undefined || value === "") {
    selectedRoleId.value = undefined;
    checkedMenuIds.value = [];
    return;
  }

  const roleId = Number(value);
  if (Number.isNaN(roleId)) {
    return;
  }

  selectedRoleId.value = roleId;
  await loadRoleMenus(roleId);
};

const saveRoleMenus = async () => {
  if (selectedRoleId.value === undefined) {
    MessagePlugin.warning("请先选择角色");
    return;
  }

  savingRoleMenus.value = true;
  try {
    const menuIds = checkedMenuIds.value
      .map((id) => Number(id))
      .filter((id) => !Number.isNaN(id));
    await rbacApi.assignRoleMenus(selectedRoleId.value, menuIds);
    notifyPermissionUpdated();
  } finally {
    savingRoleMenus.value = false;
  }
};

const openCreateRoleDialog = () => {
  roleDialogMode.value = "create";
  editingRoleId.value = undefined;
  resetRoleForm();
  roleDialogVisible.value = true;
};

const openEditRoleDialog = (role: RbacRole) => {
  roleDialogMode.value = "edit";
  editingRoleId.value = role.roleId;
  roleFormData.roleName = role.roleName;
  roleFormData.roleKey = role.roleKey;
  roleFormData.roleSort = role.roleSort ?? 0;
  roleFormData.status = role.status;
  roleFormData.remark = role.remark || "";
  roleDialogVisible.value = true;
};

const onRoleSubmit = async (context: SubmitContext) => {
  if (context.validateResult !== true) {
    return;
  }

  roleSubmitting.value = true;
  try {
    const payload: RoleMutationParam = {
      roleName: roleFormData.roleName.trim(),
      roleKey: roleFormData.roleKey.trim(),
      roleSort: Number(roleFormData.roleSort || 0),
      status: Number(roleFormData.status || 0),
      remark: roleFormData.remark?.trim() || undefined,
    };

    if (roleDialogMode.value === "create") {
      await rbacApi.createRole(payload);
    } else if (editingRoleId.value !== undefined) {
      await rbacApi.updateRole(editingRoleId.value, payload);
    }

    roleDialogVisible.value = false;
    await Promise.all([loadRoles(), loadUsers()]);
    notifyPermissionUpdated();
  } finally {
    roleSubmitting.value = false;
  }
};

const deleteRole = async (role: RbacRole) => {
  await rbacApi.deleteRole(role.roleId);
  await Promise.all([loadRoles(), loadUsers()]);
  notifyPermissionUpdated();
};

const onMenuTypeChanged = () => {
  if (menuFormData.type === 0) {
    menuFormData.permission = "";
  }
  if (menuFormData.type === 2 && (!menuFormData.parentId || menuFormData.parentId <= 0)) {
    menuFormData.parentId = undefined;
  }
};

const openCreateMenuDialog = () => {
  menuDialogMode.value = "create";
  editingMenuId.value = undefined;
  resetMenuForm();
  menuDialogVisible.value = true;
};

const openEditMenuDialog = (menu: RbacMenu) => {
  menuDialogMode.value = "edit";
  editingMenuId.value = menu.id;
  menuFormData.parentId = menu.parentId ?? 0;
  menuFormData.title = menu.title || "";
  menuFormData.name = menu.name || "";
  menuFormData.path = menu.path || "";
  menuFormData.component = menu.component || "";
  menuFormData.icon = menu.icon || "";
  menuFormData.sortOrder = menu.sortOrder ?? 0;
  menuFormData.type = menu.type;
  menuFormData.permission = menu.permission || "";
  menuFormData.status = menu.status ?? 1;
  menuFormData.hidden = menu.hidden ?? false;
  menuFormData.keepAlive = menu.keepAlive ?? false;
  menuFormData.redirect = menu.redirect || "";
  menuDialogVisible.value = true;
};

const onMenuSubmit = async (context: SubmitContext) => {
  if (context.validateResult !== true) {
    return;
  }

  menuSubmitting.value = true;
  try {
    const payload: MenuMutationParam = {
      parentId: menuFormData.parentId && menuFormData.parentId > 0 ? menuFormData.parentId : 0,
      title: menuFormData.title.trim(),
      name: menuFormData.name?.trim() || undefined,
      path: menuFormData.path?.trim() || undefined,
      component: menuFormData.component?.trim() || undefined,
      icon: menuFormData.icon?.trim() || undefined,
      sortOrder: Number(menuFormData.sortOrder || 0),
      type: Number(menuFormData.type),
      permission:
        menuFormData.type === 0 ? undefined : menuFormData.permission?.trim() || undefined,
      status: Number(menuFormData.status),
      hidden: Boolean(menuFormData.hidden),
      keepAlive: Boolean(menuFormData.keepAlive),
      redirect: menuFormData.redirect?.trim() || undefined,
    };

    if (menuDialogMode.value === "create") {
      await rbacApi.createMenu(payload);
    } else if (editingMenuId.value !== undefined) {
      await rbacApi.updateMenu(editingMenuId.value, payload);
    }

    menuDialogVisible.value = false;
    await loadMenus();
    if (selectedRoleId.value !== undefined) {
      await loadRoleMenus(selectedRoleId.value);
    }
    notifyPermissionUpdated();
  } finally {
    menuSubmitting.value = false;
  }
};

const deleteMenu = async (menu: RbacMenu) => {
  await rbacApi.deleteMenu(menu.id);
  await loadMenus();
  if (selectedRoleId.value !== undefined) {
    await loadRoleMenus(selectedRoleId.value);
  }
  notifyPermissionUpdated();
};

const batchAppendRoles = async () => {
  if (!batchUserIds.value.length) {
    MessagePlugin.warning("请选择至少一个用户");
    return;
  }
  if (!batchRoleIds.value.length) {
    MessagePlugin.warning("请选择至少一个角色");
    return;
  }

  batchAppending.value = true;
  try {
    await rbacApi.batchAppendUserRoles(batchUserIds.value, batchRoleIds.value);
    batchUserIds.value = [];
    batchRoleIds.value = [];
    await loadUsers();
    notifyPermissionUpdated();
  } finally {
    batchAppending.value = false;
  }
};

onMounted(async () => {
  await refreshAll();
});
</script>
