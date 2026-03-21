<template>
  <t-card :bordered="false" class="shadow-sm">
    <template #title>
      <div class="flex items-center gap-2">
        <t-icon name="secured" class="text-blue-600" />
        <span class="text-lg font-bold text-gray-800">最小权限管理</span>
      </div>
    </template>

    <div class="grid grid-cols-1 gap-6 xl:grid-cols-2">
      <section>
        <div class="mb-3 flex items-center justify-between">
          <h3 class="text-base font-semibold text-gray-800">用户分配角色</h3>
          <t-tag size="small" variant="light-outline">管理员专用</t-tag>
        </div>

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

          <template #operation="{ row }">
            <t-button theme="primary" variant="outline" size="small" @click="openUserRoleDialog(row)">
              分配角色
            </t-button>
          </template>
        </t-table>
      </section>

      <section>
        <div class="mb-3 flex items-center justify-between">
          <h3 class="text-base font-semibold text-gray-800">角色分配菜单</h3>
          <t-select
            v-model="selectedRoleId"
            placeholder="选择角色"
            clearable
            class="w-[240px]"
            @change="onRoleChange"
          >
            <t-option
              v-for="role in activeRoles"
              :key="role.roleId"
              :label="`${role.roleName} (${role.roleKey})`"
              :value="role.roleId"
            />
          </t-select>
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

        <div class="mt-3 flex justify-end">
          <t-button
            theme="primary"
            :disabled="selectedRoleId === undefined"
            :loading="saveRoleMenusLoading"
            @click="saveRoleMenus"
          >
            保存菜单授权
          </t-button>
        </div>
      </section>
    </div>
  </t-card>

  <t-dialog v-model:visible="userRoleDialogVisible" header="分配角色" width="520px">
    <div class="mb-3 text-sm text-gray-500">用户：{{ editingUsername || "-" }}</div>

    <t-checkbox-group v-model="editingRoleIds" class="grid grid-cols-1 gap-2">
      <t-checkbox v-for="role in activeRoles" :key="role.roleId" :value="role.roleId">
        {{ role.roleName }} ({{ role.roleKey }})
      </t-checkbox>
    </t-checkbox-group>

    <template #footer>
      <t-space>
        <t-button variant="outline" @click="userRoleDialogVisible = false">取消</t-button>
        <t-button theme="primary" :loading="saveUserRolesLoading" @click="saveUserRoles">确定</t-button>
      </t-space>
    </template>
  </t-dialog>
</template>

<script setup lang="ts">
import { rbacApi, type RbacMenu, type RbacRole, type RbacUser } from "@/api/rbac";
import { MessagePlugin, type PrimaryTableCol, type TableRowData } from "tdesign-vue-next";
import { computed, onMounted, ref } from "vue";

type MenuTreeNode = {
  label: string;
  value: number;
  children?: MenuTreeNode[];
};

const users = ref<RbacUser[]>([]);
const roles = ref<RbacRole[]>([]);
const menus = ref<RbacMenu[]>([]);

const usersLoading = ref(false);
const saveUserRolesLoading = ref(false);
const saveRoleMenusLoading = ref(false);

const userRoleDialogVisible = ref(false);
const editingUserId = ref<number | null>(null);
const editingUsername = ref("");
const editingRoleIds = ref<number[]>([]);

const selectedRoleId = ref<number | undefined>(undefined);
const checkedMenuIds = ref<Array<number | string>>([]);

const userColumns: PrimaryTableCol<TableRowData>[] = [
  { colKey: "username", title: "用户名", width: 130 },
  { colKey: "nickname", title: "昵称", width: 130 },
  { colKey: "status", title: "状态", width: 90, cell: "status" },
  { colKey: "roles", title: "角色", minWidth: 220, cell: "roles" },
  { colKey: "operation", title: "操作", width: 110, align: "center", cell: "operation" },
];

const activeRoles = computed(() => roles.value.filter((role) => role.status === 1));

const roleLabelMap = computed(() => {
  return new Map<number, string>(
    roles.value.map((role) => [role.roleId, `${role.roleName} (${role.roleKey})`])
  );
});

const menuTreeData = computed<MenuTreeNode[]>(() => {
  const effectiveMenus = menus.value.filter((menu) => menu.status !== 0);
  if (!effectiveMenus.length) {
    return [];
  }

  const nodeMap = new Map<number, MenuTreeNode>();
  effectiveMenus.forEach((menu) => {
    const permissionSuffix = menu.permission ? ` [${menu.permission}]` : "";
    nodeMap.set(menu.id, {
      label: `${menu.title}${permissionSuffix}`,
      value: menu.id,
      children: [],
    });
  });

  const roots: MenuTreeNode[] = [];
  effectiveMenus.forEach((menu) => {
    const current = nodeMap.get(menu.id);
    if (!current) return;

    if (menu.parentId && nodeMap.has(menu.parentId)) {
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

const fetchUsers = async () => {
  usersLoading.value = true;
  try {
    users.value = (await rbacApi.listUsers()) || [];
  } finally {
    usersLoading.value = false;
  }
};

const fetchRoles = async () => {
  roles.value = (await rbacApi.listRoles()) || [];
  if (selectedRoleId.value === undefined && activeRoles.value.length > 0) {
    selectedRoleId.value = activeRoles.value[0].roleId;
    await loadRoleMenus(selectedRoleId.value);
  }
};

const fetchMenus = async () => {
  menus.value = (await rbacApi.listMenus()) || [];
};

const loadRoleMenus = async (roleId: number) => {
  checkedMenuIds.value = (await rbacApi.getRoleMenuIds(roleId)) || [];
};

const onRoleChange = async (value: unknown) => {
  if (Array.isArray(value)) return;
  if (value === null || value === undefined || value === "") {
    selectedRoleId.value = undefined;
    checkedMenuIds.value = [];
    return;
  }
  const roleId = Number(value);
  selectedRoleId.value = roleId;
  await loadRoleMenus(roleId);
};

const openUserRoleDialog = (user: RbacUser) => {
  editingUserId.value = user.id;
  editingUsername.value = user.username;
  editingRoleIds.value = [...(user.roleIds || [])];
  userRoleDialogVisible.value = true;
};

const saveUserRoles = async () => {
  if (!editingUserId.value) return;
  saveUserRolesLoading.value = true;
  try {
    await rbacApi.assignUserRoles(editingUserId.value, editingRoleIds.value);
    MessagePlugin.success("用户角色已更新");
    userRoleDialogVisible.value = false;
    await fetchUsers();
  } finally {
    saveUserRolesLoading.value = false;
  }
};

const saveRoleMenus = async () => {
  if (selectedRoleId.value === undefined) {
    MessagePlugin.warning("请先选择角色");
    return;
  }
  saveRoleMenusLoading.value = true;
  try {
    const menuIds = checkedMenuIds.value.map((id) => Number(id)).filter((id) => !Number.isNaN(id));
    await rbacApi.assignRoleMenus(selectedRoleId.value, menuIds);
    MessagePlugin.success("角色菜单授权已更新");
  } finally {
    saveRoleMenusLoading.value = false;
  }
};

onMounted(async () => {
  await Promise.all([fetchMenus(), fetchRoles(), fetchUsers()]);
});
</script>
