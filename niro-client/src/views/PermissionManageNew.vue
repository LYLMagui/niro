<template>
  <PageFrame :is-mobile="false" desktop-outer-class="!p-0" desktop-content-class="p-0">
    <div class="flex h-full min-h-0 flex-col overflow-hidden bg-white">
      <PageHeader title="权限与角色管理">
        <template #icon>
          <svg class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"
            />
          </svg>
        </template>
        <template #extra>
          <div
            v-if="canReadResource || canReadRoleAuth"
            class="flex shrink-0 flex-wrap items-center gap-3"
          >
            <t-button
              v-if="canReadResource || canReadRoleAuth"
              variant="outline"
              class="!rounded"
              :loading="resourcesLoading || roleResourceLoading || usersLoading"
              @click="reloadWorkbench"
            >
              刷新草稿
            </t-button>
            <t-button
              v-if="canSaveResource"
              variant="outline"
              class="!rounded"
              @click="resourceDrawerVisible = true"
            >
              资源工作台
            </t-button>
            <t-button
              v-if="canAssignUserRole"
              variant="outline"
              class="!rounded"
              @click="openUserRoleDrawer"
            >
              用户分配角色
            </t-button>
            <t-button
              v-if="canPreviewRole"
              variant="outline"
              class="!rounded"
              :disabled="!selectedRoleId"
              @click="openPreviewDrawer"
            >
              角色预览
            </t-button>
            <t-button
              v-if="canValidatePublish"
              theme="warning"
              variant="outline"
              class="!rounded"
              :loading="publishValidating"
              @click="openValidateDrawer"
            >
              发布校验
            </t-button>
            <t-button
              v-if="canPublish"
              theme="primary"
              class="!rounded"
              :loading="publishing"
              @click="handlePublish"
            >
              执行发布
            </t-button>
          </div>
        </template>
      </PageHeader>

      <div class="min-h-0 flex-1 overflow-hidden">
        <div class="flex h-full min-h-0 overflow-hidden">
          <!-- 左侧：角色列表 (List Pane) -->
          <aside class="flex w-80 shrink-0 flex-col border-r border-slate-200 bg-slate-50/50">
            <!-- 列表头部工具栏 -->
            <div class="shrink-0 border-b border-slate-200 bg-white p-4">
              <div class="mb-3 flex items-center justify-between">
                <h2 class="text-sm font-medium text-slate-700">系统角色 ({{ roles.length }})</h2>
                <t-button
                  v-if="canCreateRole"
                  theme="primary"
                  variant="text"
                  size="small"
                  class="!h-auto !p-0 font-medium hover:!bg-transparent"
                  @click="openCreateRoleDialog"
                >
                  <template #icon><t-icon name="plus" /></template>
                  新增
                </t-button>
              </div>
              <t-input
                v-model="roleKeyword"
                placeholder="搜索角色名称..."
                size="small"
                class="!border-slate-200 !bg-slate-50"
              >
                <template #prefix-icon>
                  <t-icon name="search" size="14px" class="text-slate-400" />
                </template>
              </t-input>
            </div>

            <!-- 列表项 (无圆角，通栏贯穿) -->
            <div class="flex-1 overflow-y-auto">
              <div
                v-if="filteredRoles.length === 0"
                class="flex h-full items-center justify-center px-6 text-sm text-slate-400"
              >
                暂无匹配角色。
              </div>

              <button
                v-for="role in filteredRoles"
                :key="role.roleId"
                type="button"
                class="group block w-full border-b border-l-2 border-slate-100 px-4 py-3 text-left transition-colors"
                :class="
                  selectedRoleId === role.roleId
                    ? 'bg-primary-50/60 border-l-primary-600'
                    : 'border-l-transparent bg-transparent hover:bg-slate-50'
                "
                @click="selectedRoleId = role.roleId"
              >
                <div class="mb-1 flex items-center justify-between gap-3">
                  <div class="flex min-w-0 items-center gap-2">
                    <t-tooltip :content="role.roleName" placement="top-left">
                      <span
                        class="truncate text-sm font-medium transition-colors"
                        :class="
                          selectedRoleId === role.roleId
                            ? 'text-primary-700'
                            : 'group-hover:text-primary-600 text-slate-700'
                        "
                      >
                        {{ role.roleName }}
                      </span>
                    </t-tooltip>
                    <span
                      v-if="isSystemRole(role)"
                      class="ml-1 rounded-sm border border-slate-200 px-1 text-[10px] text-slate-400"
                    >
                      系统
                    </span>
                    <span
                      v-if="isRoleDirty(role.roleId)"
                      class="ml-1 rounded-sm border border-orange-200 bg-orange-50 px-1 text-[10px] text-orange-500"
                    >
                      待保存
                    </span>
                  </div>
                  <span
                    class="shrink-0 text-[10px] transition-colors"
                    :class="selectedRoleId === role.roleId ? 'text-slate-500' : 'text-slate-400'"
                  >
                    {{ roleMemberCountMap.get(role.roleId) || 0 }} 成员
                  </span>
                </div>
                <t-tooltip :content="roleListDescription(role)" placement="top-left">
                  <p class="line-clamp-1 text-xs text-slate-500">
                    {{ roleListDescription(role) }}
                  </p>
                </t-tooltip>
              </button>
            </div>
          </aside>

          <!-- 右侧：权限配置明细 (Detail Pane) -->
          <section class="relative flex flex-1 flex-col overflow-hidden bg-white">
            <!-- 详情头部 (信息+操作) -->
            <div
              v-if="selectedRole"
              class="flex shrink-0 items-start justify-between border-b border-slate-200 bg-white px-8 py-6"
            >
              <div>
                <div class="mb-1.5 flex items-center gap-3">
                  <h2 class="text-xl font-semibold text-slate-800">{{ selectedRole.roleName }}</h2>
                  <span
                    v-if="selectedRole.status === 1"
                    class="rounded border border-green-200 bg-green-50 px-2 py-0.5 text-xs font-medium text-green-700"
                  >
                    启用中
                  </span>
                  <span
                    v-else
                    class="rounded border border-orange-200 bg-orange-50 px-2 py-0.5 text-xs font-medium text-orange-700"
                  >
                    停用中
                  </span>
                  <span
                    v-if="selectedRoleDirty"
                    class="rounded border border-blue-200 bg-blue-50 px-2 py-0.5 text-xs font-medium text-blue-700"
                  >
                    草稿已修改
                  </span>
                  <span
                    v-if="selectedRoleLocked"
                    class="rounded border border-amber-200 bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-700"
                  >
                    全权限锁定
                  </span>
                </div>
                <p class="max-w-2xl text-sm leading-relaxed text-slate-500">
                  {{ selectedRoleDescription }}
                </p>
              </div>
              <div v-if="canCopyRole || canDeleteRole" class="flex gap-2">
                <t-button
                  v-if="canCopyRole"
                  variant="outline"
                  size="small"
                  class="!rounded hover:!bg-slate-50"
                  @click="openCopyRoleDialog"
                >
                  复制角色
                </t-button>
                <t-button
                  v-if="canDeleteRole"
                  theme="danger"
                  variant="outline"
                  size="small"
                  class="!rounded hover:!bg-red-50"
                  :disabled="!canDeleteSelectedRole"
                  @click="handleDeleteSelectedRole"
                >
                  删除
                </t-button>
              </div>
            </div>

            <!-- 权限配置区域 (Tab 结构) -->
            <div class="flex min-h-0 flex-1 flex-col overflow-hidden">
              <!-- Tab 切换 -->
              <div
                v-if="selectedRole"
                class="flex shrink-0 items-center gap-8 border-b border-slate-200 bg-white px-8"
              >
                <button
                  v-for="tab in permissionTabs"
                  :key="tab"
                  class="relative h-12 border-b-2 text-sm font-medium transition-all"
                  :class="
                    activeTab === tab
                      ? 'text-primary-600 border-primary-600'
                      : 'border-transparent text-slate-500 hover:text-slate-700'
                  "
                  @click="activeTab = tab"
                >
                  {{ tabLabelMap[tab] }}
                  <span
                    v-if="tab === 'ACTION' && selectedRoleAssignedCount > 0"
                    class="ml-1 text-[10px] text-slate-400"
                  >
                    ({{ selectedRoleAssignedCount }})
                  </span>
                </button>
              </div>

              <div class="flex-1 overflow-y-auto px-8 py-6">
                <div
                  v-if="!selectedRole"
                  class="flex h-full items-center justify-center px-6 text-sm text-slate-400"
                >
                  请先从左侧选择一个角色。
                </div>

                <div v-else class="max-w-5xl">
                  <div
                    v-if="activePermissionModules.length === 0"
                    class="flex h-full items-center justify-center px-6 text-sm text-slate-400"
                  >
                    {{ activePermissionEmptyText }}
                  </div>

                  <div v-else>
                    <div v-if="isTreePermissionTab" class="space-y-3">
                      <section
                        v-for="module in activeTreePermissionModules"
                        :key="module.id"
                        class="overflow-hidden rounded-lg border border-slate-200 bg-white"
                      >
                        <div class="flex items-center justify-between gap-4 bg-slate-50 px-4 py-3">
                          <button
                            type="button"
                            class="flex min-w-0 flex-1 items-center gap-2 text-left"
                            :aria-expanded="!isTreePermissionModuleCollapsed(module.id)"
                            @click="toggleTreePermissionModuleCollapsed(module.id)"
                          >
                            <t-icon
                              :name="
                                isTreePermissionModuleCollapsed(module.id)
                                  ? 'chevron-right'
                                  : 'chevron-down'
                              "
                              class="shrink-0 text-slate-500"
                            />
                            <div class="min-w-0">
                              <t-tooltip :content="module.title" placement="top-left">
                                <h3 class="truncate text-base font-semibold text-slate-800">
                                  {{ module.title }}
                                </h3>
                              </t-tooltip>
                              <p v-if="module.hint" class="mt-1 text-xs text-slate-400">
                                {{ module.hint }}
                              </p>
                            </div>
                          </button>
                          <t-checkbox
                            v-if="module.resourceIds.length > 0"
                            :checked="module.checked"
                            :indeterminate="module.indeterminate"
                            :disabled="rolePermissionLocked"
                            @click.stop
                            @change="(checked) => toggleModuleResources(module, checked)"
                          >
                            {{ activeTreeModuleCheckAllText }}
                          </t-checkbox>
                          <span v-else class="text-xs text-slate-400">待接入</span>
                        </div>

                        <div
                          v-if="!isTreePermissionModuleCollapsed(module.id)"
                          class="divide-y divide-slate-100"
                        >
                          <div
                            v-for="row in module.rows"
                            :key="row.key"
                            class="grid grid-cols-12 gap-4 px-4 py-3 transition-colors hover:bg-slate-50/60"
                          >
                            <div class="col-span-4 flex min-w-0 items-center">
                              <div
                                class="min-w-0 text-sm font-medium text-slate-700"
                                :class="row.level > 1 ? 'pl-4' : ''"
                              >
                                <t-tooltip :content="row.title" placement="top-left">
                                  <span class="truncate">{{ row.title }}</span>
                                </t-tooltip>
                                <span
                                  v-if="row.hint"
                                  class="ml-2 text-xs font-normal text-slate-400"
                                >
                                  {{ row.hint }}
                                </span>
                              </div>
                            </div>
                            <div class="col-span-8 flex flex-wrap items-center gap-x-7 gap-y-2">
                              <template v-if="row.actions.length > 0">
                                <t-checkbox
                                  v-for="action in row.actions"
                                  :key="action.id"
                                  :checked="isSelectedRoleResource(action.id)"
                                  :disabled="rolePermissionLocked"
                                  @change="
                                    (checked) => toggleTreePermissionRow(row, action.id, checked)
                                  "
                                >
                                  {{ action.label }}
                                </t-checkbox>
                              </template>
                              <span v-else class="text-sm text-slate-400">
                                {{ activeTreeEmptyRowText }}
                              </span>
                            </div>
                          </div>
                        </div>
                      </section>
                    </div>

                    <div v-else>
                      <section
                        v-for="module in activePermissionModules"
                        :key="module.id"
                        class="mb-8 last:mb-0"
                      >
                        <div
                          class="flex items-center justify-between border-b border-slate-200 pb-3"
                        >
                          <div class="min-w-0">
                            <t-tooltip :content="module.title" placement="top-left">
                              <h3 class="truncate text-base font-semibold text-slate-800">
                                {{ module.title }}
                              </h3>
                            </t-tooltip>
                            <p v-if="module.hint" class="mt-1 text-xs text-slate-400">
                              {{ module.hint }}
                            </p>
                          </div>
                          <t-checkbox
                            v-if="module.resourceIds.length > 0"
                            :checked="module.checked"
                            :indeterminate="module.indeterminate"
                            :disabled="rolePermissionLocked"
                            @change="(checked) => toggleModuleResources(module, checked)"
                          >
                            {{ activeModuleCheckAllText }}
                          </t-checkbox>
                          <span v-else class="text-xs text-slate-400">待接入</span>
                        </div>

                        <div>
                          <div
                            v-for="row in module.rows"
                            :key="row.key"
                            class="grid grid-cols-12 gap-4 border-b border-slate-100 py-3 transition-colors hover:bg-slate-50/60"
                          >
                            <div class="col-span-3 flex min-w-0 items-center">
                              <div
                                class="min-w-0 text-sm font-medium text-slate-700"
                                :class="row.level > 0 ? 'pl-4' : ''"
                              >
                                <t-tooltip :content="row.title" placement="top-left">
                                  <span class="truncate">{{ row.title }}</span>
                                </t-tooltip>
                                <span
                                  v-if="row.hint"
                                  class="ml-2 text-xs font-normal text-slate-400"
                                >
                                  {{ row.hint }}
                                </span>
                              </div>
                            </div>
                            <div class="col-span-9 flex flex-wrap items-center gap-x-7 gap-y-2">
                              <template v-if="row.actions.length > 0">
                                <t-checkbox
                                  v-for="action in row.actions"
                                  :key="action.id"
                                  :checked="isSelectedRoleResource(action.id)"
                                  :disabled="rolePermissionLocked"
                                  @change="
                                    (checked) => toggleSelectedRolePermission(action.id, checked)
                                  "
                                >
                                  {{ action.label }}
                                </t-checkbox>
                              </template>
                              <span v-else class="text-sm text-slate-400">
                                后续接入真实数据后配置
                              </span>
                            </div>
                          </div>
                        </div>
                      </section>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 底部保存操作区 (极简固定底栏) -->
            <div
              class="flex shrink-0 items-center justify-between border-t border-slate-200 bg-white px-8 py-4"
            >
              <span class="flex items-center text-sm text-slate-500">
                <t-icon name="info-circle" class="mr-1.5 text-slate-400" />
                修改权限后，该角色下属成员需重新登录生效
              </span>
              <div class="flex gap-3">
                <t-button
                  variant="outline"
                  class="!rounded"
                  :disabled="
                    !selectedRoleId ||
                    !selectedRoleDirty ||
                    savingRoleResources ||
                    selectedRoleLocked
                  "
                  @click="resetSelectedRoleDraft"
                >
                  取消修改
                </t-button>
                <t-button
                  theme="primary"
                  class="!rounded"
                  :disabled="dirtySavableRoleIds.length === 0 || !canSaveRoleAuth"
                  :loading="savingRoleResources"
                  @click="saveRoleResources"
                >
                  保存配置
                </t-button>
              </div>
            </div>
          </section>
        </div>
      </div>

      <t-drawer
        v-model:visible="resourceDrawerVisible"
        header="资源工作台"
        size="1180px"
        :close-btn="true"
        :footer="false"
      >
        <div class="flex h-full min-h-0 overflow-hidden bg-white">
          <section
            class="flex w-[320px] shrink-0 flex-col overflow-hidden border-r border-slate-200 bg-slate-50/75"
          >
            <div class="border-b border-slate-200 bg-white px-4 py-4">
              <div class="flex items-start justify-between gap-2">
                <div>
                  <div class="text-base font-semibold text-slate-900">资源目录</div>
                  <div class="mt-1 text-xs text-slate-500">
                    按标题、唯一键和资源类型快速定位当前草稿资源。
                  </div>
                </div>
                <t-tag size="small" variant="light-outline">{{ filteredResourceCount }}</t-tag>
              </div>
              <div class="mt-4 space-y-2">
                <t-input
                  v-model="keyword"
                  clearable
                  size="small"
                  placeholder="搜索标题 / resourceKey / 权限码"
                />
                <t-select
                  v-model="resourceTypeFilter"
                  clearable
                  size="small"
                  placeholder="资源类型"
                >
                  <t-option value="PAGE" label="页面" />
                  <t-option value="MENU" label="菜单" />
                  <t-option value="BUTTON" label="按钮" />
                </t-select>
              </div>
              <div v-if="canSaveResource" class="mt-4 grid grid-cols-3 gap-2">
                <t-button size="small" variant="outline" @click="openCreateResource('PAGE')">
                  新增页面
                </t-button>
                <t-button size="small" variant="outline" @click="openCreateResource('MENU')">
                  新增菜单
                </t-button>
                <t-button size="small" variant="outline" @click="openCreateResource('BUTTON')">
                  新增按钮
                </t-button>
              </div>
            </div>

            <div
              class="border-b border-slate-200 bg-slate-50/70 px-4 py-3 text-xs leading-5 text-slate-600"
            >
              资源编辑仍采用草稿机制；页面走 pageKey，菜单可作为目录或页面入口，按钮走
              permissionCode。
            </div>

            <t-loading :loading="resourcesLoading" text="加载草稿资源中..." class="min-h-0 flex-1">
              <div class="min-h-0 flex-1 overflow-auto">
                <div
                  v-if="filteredTreeData.length === 0"
                  class="flex h-full items-center justify-center px-6 text-sm text-slate-400"
                >
                  暂无资源，先新增页面、菜单或按钮。
                </div>

                <t-tree
                  v-else
                  activable
                  hover
                  expand-all
                  transition
                  :data="filteredTreeData"
                  :keys="treeKeys"
                  :actived="activedTreeValues"
                  @active="handleTreeActive"
                >
                  <template #label="{ node }">
                    <div
                      class="flex min-w-0 cursor-pointer items-start gap-3 border-l-2 px-4 py-3 transition-colors"
                      :class="
                        node.actived
                          ? 'border-blue-600 bg-white text-slate-900'
                          : 'border-transparent hover:bg-white/80'
                      "
                    >
                      <div
                        class="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-sm text-[11px] font-semibold ring-1 ring-inset"
                        :class="
                          node.data.resourceType === 'PAGE'
                            ? 'bg-blue-50 text-blue-600 ring-blue-100'
                            : node.data.resourceType === 'MENU'
                              ? 'bg-emerald-50 text-emerald-600 ring-emerald-100'
                              : 'bg-amber-50 text-amber-600 ring-amber-100'
                        "
                      >
                        {{
                          node.data.resourceType === "PAGE"
                            ? "页"
                            : node.data.resourceType === "MENU"
                              ? "菜"
                              : "钮"
                        }}
                      </div>
                      <div class="min-w-0 flex-1">
                        <div class="flex items-center justify-between gap-3">
                          <div class="truncate text-sm font-medium text-slate-800">
                            {{ node.data.title }}
                          </div>
                          <t-tag
                            :theme="resourceTypeTheme(node.data.resourceType)"
                            size="small"
                            variant="light-outline"
                          >
                            {{ resourceTypeLabel(node.data.resourceType) }}
                          </t-tag>
                        </div>
                        <div class="mt-1 truncate text-xs text-slate-400">
                          {{
                            node.data.pageKey || node.data.permissionCode || node.data.resourceKey
                          }}
                        </div>
                      </div>
                    </div>
                  </template>
                </t-tree>
              </div>
            </t-loading>
          </section>

          <section class="min-w-0 flex-1 overflow-hidden bg-white">
            <div class="border-b border-slate-200 bg-white px-6 py-5">
              <div class="flex flex-wrap items-start justify-between gap-4">
                <div class="min-w-0 flex-1">
                  <div class="flex flex-wrap items-center gap-2">
                    <h2 class="text-[22px] font-semibold tracking-tight text-slate-900">
                      {{ selectedResource ? selectedResource.title : "资源详情" }}
                    </h2>
                    <t-tag
                      v-if="selectedResource"
                      :theme="resourceTypeTheme(formData.resourceType)"
                      variant="light-outline"
                      size="small"
                    >
                      {{ resourceTypeLabel(formData.resourceType) }}
                    </t-tag>
                    <t-tag
                      v-if="selectedResource"
                      :theme="formData.status === 1 ? 'success' : 'warning'"
                      variant="light-outline"
                      size="small"
                    >
                      {{ formData.status === 1 ? "启用中" : "停用中" }}
                    </t-tag>
                    <t-tag
                      v-if="selectedResource && formData.hidden"
                      variant="light-outline"
                      size="small"
                    >
                      已隐藏
                    </t-tag>
                  </div>
                  <p class="mt-2 max-w-3xl text-sm leading-6 text-slate-500">
                    {{
                      selectedResource
                        ? "维护当前资源的唯一键、层级关系和页面绑定；修改只写入草稿，发布后才会影响新权限导航。"
                        : "请选择左侧资源，或直接新增一个页面、菜单或按钮资源。"
                    }}
                  </p>
                  <div
                    class="mt-4 flex flex-wrap items-center gap-x-4 gap-y-2 text-xs text-slate-500"
                  >
                    <span>
                      模式：
                      <span class="font-medium text-slate-700">{{ currentEditorModeText }}</span>
                    </span>
                    <span>
                      父级：
                      <span class="font-medium text-slate-700">
                        {{ currentParentResourceTitle }}
                      </span>
                    </span>
                    <span>
                      {{ currentBindingLabel }}：
                      <span class="font-medium text-slate-700">{{ currentBindingValue }}</span>
                    </span>
                    <span>
                      状态：
                      <span class="font-medium text-slate-700">{{ currentVisibilityText }}</span>
                    </span>
                  </div>
                </div>
                <div
                  class="w-full shrink-0 border-t border-slate-200 pt-4 text-xs text-slate-400 sm:w-auto sm:border-t-0 sm:pt-0 sm:text-right"
                >
                  <div class="font-medium tracking-[0.2em] text-slate-400 uppercase">
                    Resource Key
                  </div>
                  <div class="mt-2 max-w-[260px] text-sm font-medium break-all text-slate-600">
                    {{ formData.resourceKey || "未填写" }}
                  </div>
                </div>
              </div>
            </div>

            <t-loading :loading="detailLoading" text="加载资源详情中..." class="min-h-0 flex-1">
              <div class="flex h-full min-h-0 flex-col">
                <div
                  v-if="!isEditingResource"
                  class="flex flex-1 items-center justify-center px-6 text-sm text-slate-400"
                >
                  请选择左侧资源，或先新增一个资源。
                </div>

                <div v-else class="min-h-0 flex-1 overflow-auto px-6 py-6">
                  <t-form
                    ref="resourceFormRef"
                    :data="formData"
                    :rules="formRules"
                    :disabled="!canSaveResource"
                    label-align="top"
                    class="grid grid-cols-1 gap-x-6 gap-y-5 pb-1 xl:grid-cols-2"
                    @submit="(context) => void handleSaveResource(context)"
                  >
                    <div class="border-b border-slate-200 pb-3 xl:col-span-2">
                      <div class="text-sm font-medium text-slate-900">基础信息</div>
                      <div class="mt-1 text-xs text-slate-500">
                        先定义资源类型、唯一键和层级位置，再补充展示字段。
                      </div>
                    </div>

                    <t-form-item label="资源类型" name="resourceType">
                      <t-radio-group v-model="formData.resourceType" variant="outline">
                        <t-radio value="PAGE">页面</t-radio>
                        <t-radio value="MENU">菜单</t-radio>
                        <t-radio value="BUTTON">按钮</t-radio>
                      </t-radio-group>
                    </t-form-item>

                    <t-form-item label="资源标题" name="title">
                      <t-input
                        v-model="formData.title"
                        clearable
                        placeholder="例如：邀请管理、发布按钮"
                      />
                    </t-form-item>

                    <t-form-item label="Resource Key" name="resourceKey">
                      <t-input
                        v-model="formData.resourceKey"
                        clearable
                        placeholder="保持全局唯一，建议使用业务语义键"
                      />
                    </t-form-item>

                    <t-form-item label="父级资源" name="parentResourceId">
                      <t-select
                        v-model="formData.parentResourceId"
                        clearable
                        filterable
                        placeholder="根节点或挂到已有页面 / 菜单下"
                        :options="parentResourceOptions"
                      />
                    </t-form-item>

                    <t-form-item label="排序值" name="sortOrder">
                      <t-input
                        v-model="formData.sortOrder"
                        type="number"
                        placeholder="默认 0，越小越靠前"
                      />
                    </t-form-item>

                    <t-form-item label="状态" name="status">
                      <t-radio-group v-model="formData.status" variant="outline">
                        <t-radio :value="1">启用</t-radio>
                        <t-radio :value="0">停用</t-radio>
                      </t-radio-group>
                    </t-form-item>

                    <t-form-item label="显示控制" name="hidden">
                      <div
                        class="flex min-h-[40px] items-center justify-between rounded border border-slate-200 bg-slate-50 px-3 text-sm text-slate-600"
                      >
                        <span>
                          {{ formData.hidden ? "当前资源将隐藏展示" : "当前资源将正常展示" }}
                        </span>
                        <t-switch v-model="formData.hidden" size="small" />
                      </div>
                    </t-form-item>

                    <div class="border-b border-slate-200 pt-2 pb-3 xl:col-span-2">
                      <div class="text-sm font-medium text-slate-900">资源绑定</div>
                      <div class="mt-1 text-xs text-slate-500">
                        页面资源绑定 pageKey；菜单可作为无 pageKey 目录，也可绑定 pageKey
                        作为页面入口；按钮走 permissionCode 绑定。
                      </div>
                    </div>

                    <template v-if="formData.resourceType !== 'BUTTON'">
                      <t-form-item label="页面绑定" name="pageKey">
                        <t-select
                          v-model="formData.pageKey"
                          filterable
                          clearable
                          placeholder="请选择 pageKey"
                          :options="pageOptions"
                        />
                      </t-form-item>

                      <t-form-item label="图标" name="icon">
                        <t-input
                          v-model="formData.icon"
                          clearable
                          placeholder="例如：setting、shield、user"
                        />
                      </t-form-item>

                      <div
                        class="rounded border border-slate-200 bg-slate-50/80 px-4 py-4 xl:col-span-2"
                      >
                        <div class="flex flex-wrap items-start justify-between gap-3">
                          <div class="min-w-0 flex-1">
                            <div class="text-sm font-medium text-slate-800">页面注册表命中</div>
                            <div class="mt-1 text-xs leading-5 text-slate-500">
                              {{ pageInfoText }}
                            </div>
                          </div>
                          <t-tag
                            :theme="currentPageRegistry ? 'success' : 'warning'"
                            variant="light-outline"
                            size="small"
                          >
                            {{ currentPageRegistry ? "已命中注册表" : "未命中注册表" }}
                          </t-tag>
                        </div>
                        <div
                          class="mt-4 grid grid-cols-1 gap-3 text-sm text-slate-600 xl:grid-cols-3"
                        >
                          <div>
                            <div class="text-xs tracking-[0.16em] text-slate-400 uppercase">
                              页面标题
                            </div>
                            <div class="mt-1 break-all text-slate-800">
                              {{ currentPageRegistry?.meta.title || "未匹配" }}
                            </div>
                          </div>
                          <div>
                            <div class="text-xs tracking-[0.16em] text-slate-400 uppercase">
                              路由路径
                            </div>
                            <div class="mt-1 break-all text-slate-800">
                              {{ currentPageRegistry ? `/new/${currentPageRegistry.path}` : "-" }}
                            </div>
                          </div>
                          <div>
                            <div class="text-xs tracking-[0.16em] text-slate-400 uppercase">
                              Route Name
                            </div>
                            <div class="mt-1 break-all text-slate-800">
                              {{ currentPageRegistry?.routeName || "-" }}
                            </div>
                          </div>
                        </div>
                      </div>
                    </template>

                    <template v-else>
                      <t-form-item label="权限码" name="permissionCode">
                        <t-input
                          v-model="formData.permissionCode"
                          clearable
                          placeholder="例如：system:permission:publish"
                        />
                      </t-form-item>

                      <t-form-item label="按钮分组" name="buttonGroup">
                        <t-input
                          v-model="formData.buttonGroup"
                          clearable
                          placeholder="例如：工具栏、行操作、弹窗底部"
                        />
                      </t-form-item>

                      <div
                        class="rounded border border-amber-200 bg-amber-50/70 px-4 py-4 text-sm text-amber-800 xl:col-span-2"
                      >
                        按钮资源不会参与前端路由注册，只通过 permissionCode
                        与页面上的按钮显隐能力做映射。
                      </div>
                    </template>

                    <div class="border-b border-slate-200 pt-2 pb-3 xl:col-span-2">
                      <div class="text-sm font-medium text-slate-900">补充说明</div>
                      <div class="mt-1 text-xs text-slate-500">
                        备注只用于草稿协作说明，不参与页面路由解析。
                      </div>
                    </div>

                    <t-form-item class="xl:col-span-2" label="备注" name="remark">
                      <t-textarea
                        v-model="formData.remark"
                        :autosize="{ minRows: 3, maxRows: 6 }"
                        maxlength="300"
                        placeholder="补充资源用途、命名约束或发布前提醒"
                      />
                    </t-form-item>

                    <div
                      v-if="selectedResource?.resourceType === 'PAGE'"
                      class="rounded border border-slate-200 bg-white px-4 py-4 xl:col-span-2"
                    >
                      <div
                        class="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 pb-3"
                      >
                        <div>
                          <div class="text-sm font-medium text-slate-900">当前页面资源视图</div>
                          <div class="mt-1 text-xs text-slate-500">
                            结合当前草稿层级，快速查看页面下已挂接的菜单与按钮分组。
                          </div>
                        </div>
                        <div class="flex flex-wrap items-center gap-2 text-xs text-slate-500">
                          <span>菜单 {{ selectedPageMenus.length }}</span>
                          <span class="h-1 w-1 rounded-full bg-slate-300"></span>
                          <span>按钮 {{ selectedPageButtons.length }}</span>
                          <span class="h-1 w-1 rounded-full bg-slate-300"></span>
                          <span>分组 {{ selectedPageButtonGroups.length }}</span>
                        </div>
                      </div>

                      <div
                        class="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,220px)_minmax(0,1fr)]"
                      >
                        <div>
                          <div
                            class="mb-2 text-xs font-medium tracking-[0.16em] text-slate-400 uppercase"
                          >
                            菜单挂接
                          </div>
                          <div v-if="selectedPageMenus.length === 0" class="text-sm text-slate-400">
                            当前页面下暂无菜单资源。
                          </div>
                          <div v-else class="flex flex-wrap gap-2">
                            <t-tag
                              v-for="item in selectedPageMenus"
                              :key="item.id"
                              theme="success"
                              variant="light-outline"
                              size="small"
                            >
                              {{ item.title }}
                            </t-tag>
                          </div>
                        </div>

                        <div>
                          <div
                            class="mb-2 text-xs font-medium tracking-[0.16em] text-slate-400 uppercase"
                          >
                            按钮分组
                          </div>
                          <div
                            v-if="selectedPageButtonGroups.length === 0"
                            class="text-sm text-slate-400"
                          >
                            当前页面下暂无按钮资源。
                          </div>
                          <div v-else class="space-y-3">
                            <div
                              v-for="group in selectedPageButtonGroups"
                              :key="group.group"
                              class="rounded border border-slate-200 bg-slate-50/70 px-3 py-3"
                            >
                              <div class="text-sm font-medium text-slate-800">
                                {{ group.group }}
                              </div>
                              <div class="mt-2 flex flex-wrap gap-2">
                                <t-tag
                                  v-for="button in group.buttons"
                                  :key="button.id"
                                  theme="warning"
                                  variant="light-outline"
                                  size="small"
                                >
                                  {{ button.title
                                  }}{{ button.permissionCode ? ` · ${button.permissionCode}` : "" }}
                                </t-tag>
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>

                    <div
                      class="sticky bottom-0 z-10 -mx-1 mt-2 border-t border-slate-200 bg-white/95 px-1 pt-4 backdrop-blur xl:col-span-2"
                    >
                      <div class="flex flex-wrap items-center justify-between gap-3">
                        <div class="text-xs leading-5 text-slate-500">
                          保存仅写入草稿资源；角色授权保存和最终发布需在角色工作区继续完成。
                        </div>
                        <div class="flex items-center gap-2">
                          <t-button
                            variant="outline"
                            :disabled="savingResource"
                            @click="resetSelectedResource"
                          >
                            重置当前编辑
                          </t-button>
                          <t-button
                            theme="primary"
                            type="submit"
                            :loading="savingResource"
                            :disabled="!canSaveResource"
                          >
                            保存资源草稿
                          </t-button>
                        </div>
                      </div>
                    </div>
                  </t-form>
                </div>
              </div>
            </t-loading>
          </section>
        </div>
      </t-drawer>

      <t-drawer
        v-model:visible="previewDrawerVisible"
        header="角色预览"
        size="520px"
        :close-btn="true"
        :footer="false"
      >
        <t-loading :loading="previewLoading" text="加载预览中...">
          <div v-if="previewData" class="space-y-4">
            <div class="border-l-2 border-slate-200 pl-3">
              <div class="space-y-1 text-sm text-slate-600">
                <div>角色：{{ previewData.roleName }}</div>
                <div>首页：{{ previewData.homePageTitle || previewData.homePageKey || "-" }}</div>
                <div>首页 pageKey：{{ previewData.homePageKey || "-" }}</div>
              </div>
            </div>

            <section class="space-y-2 border-t border-slate-200 pt-4">
              <div class="text-sm font-medium text-slate-800">导航树</div>
              <div v-if="previewNavigationTreeData.length === 0" class="text-sm text-slate-400">
                暂无
              </div>
              <div
                v-else
                class="max-h-[320px] overflow-auto border-l-2 border-slate-200 bg-slate-50/60 px-2 py-2"
              >
                <t-tree
                  hover
                  expand-all
                  transition
                  :data="previewNavigationTreeData"
                  :keys="treeKeys"
                >
                  <template #label="{ node }">
                    <div class="flex min-w-0 items-center gap-2 px-2 py-1.5">
                      <t-tag
                        :theme="resourceTypeTheme(node.data.resourceType)"
                        size="small"
                        variant="light-outline"
                      >
                        {{ resourceTypeLabel(node.data.resourceType) }}
                      </t-tag>
                      <span class="truncate text-sm text-slate-800">{{ node.data.title }}</span>
                      <span class="truncate text-xs text-slate-400">
                        {{ node.data.pageKey || node.data.resourceKey }}
                      </span>
                    </div>
                  </template>
                </t-tree>
              </div>
            </section>

            <section class="space-y-2 border-t border-slate-200 pt-4">
              <div class="text-sm font-medium text-slate-800">可见页面</div>
              <t-space v-if="previewData.accessiblePages.length > 0" break-line>
                <t-tag
                  v-for="item in previewData.accessiblePages"
                  :key="item.resourceId"
                  variant="light-outline"
                >
                  {{ item.title }} ({{ item.pageKey }})
                </t-tag>
              </t-space>
              <t-space v-else break-line>
                <t-tag v-for="item in previewData.visiblePages" :key="item" variant="light-outline">
                  {{ item }}
                </t-tag>
                <span v-if="previewData.visiblePages.length === 0" class="text-sm text-slate-400">
                  暂无
                </span>
              </t-space>
            </section>

            <section class="space-y-2 border-t border-slate-200 pt-4">
              <div class="text-sm font-medium text-slate-800">可见菜单</div>
              <t-space break-line>
                <t-tag
                  v-for="item in previewData.visibleMenus"
                  :key="item"
                  theme="success"
                  variant="light-outline"
                >
                  {{ item }}
                </t-tag>
                <span v-if="previewData.visibleMenus.length === 0" class="text-sm text-slate-400">
                  暂无
                </span>
              </t-space>
            </section>

            <section class="space-y-2 border-t border-slate-200 pt-4">
              <div class="text-sm font-medium text-slate-800">可用按钮</div>
              <t-space break-line>
                <t-tag
                  v-for="item in previewData.enabledButtons"
                  :key="item"
                  theme="warning"
                  variant="light-outline"
                >
                  {{ item }}
                </t-tag>
                <span v-if="previewData.enabledButtons.length === 0" class="text-sm text-slate-400">
                  暂无
                </span>
              </t-space>
            </section>

            <section class="space-y-3 border-t border-slate-200 pt-4">
              <div class="text-sm font-medium text-slate-800">页面按钮矩阵</div>
              <div v-if="previewData.pageButtons.length === 0" class="text-sm text-slate-400">
                暂无
              </div>
              <div v-else class="space-y-3">
                <div
                  v-for="item in previewData.pageButtons"
                  :key="item.resourceId"
                  class="border-l-2 border-slate-200 bg-slate-50/40 px-3 py-3"
                >
                  <div class="mb-2 flex items-center justify-between gap-2">
                    <span class="text-sm font-medium text-slate-800">{{ item.title }}</span>
                    <span class="text-xs text-slate-400">{{ item.pageKey }}</span>
                  </div>
                  <div class="flex flex-wrap gap-2">
                    <t-tag
                      v-for="button in item.buttons"
                      :key="button.resourceId"
                      theme="warning"
                      variant="light-outline"
                    >
                      {{ button.title
                      }}{{ button.permissionCode ? ` · ${button.permissionCode}` : "" }}
                    </t-tag>
                  </div>
                </div>
              </div>
            </section>
          </div>
        </t-loading>
      </t-drawer>

      <t-drawer
        v-model:visible="validateDrawerVisible"
        header="发布校验结果"
        size="480px"
        :close-btn="true"
        :footer="false"
      >
        <div class="space-y-4">
          <t-alert
            :theme="validateResult?.success ? 'success' : 'error'"
            :message="validateResult?.message || '尚未执行校验'"
            :close-btn="false"
          />

          <section class="space-y-2 border-t border-slate-200 pt-4">
            <div class="text-sm font-medium text-slate-800">版本信息</div>
            <div class="space-y-2 text-sm text-slate-600">
              <div>configVersion：{{ validateResult?.configVersion || "-" }}</div>
              <div>publishedAt：{{ validateResult?.publishedAt || "-" }}</div>
            </div>
          </section>
        </div>
      </t-drawer>

      <t-drawer
        v-model:visible="userRoleDrawerVisible"
        header="用户分配角色"
        size="640px"
        :close-btn="true"
        :footer="false"
      >
        <t-loading :loading="usersLoading" text="加载用户中...">
          <div class="space-y-4">
            <section class="space-y-4 border-t border-slate-200 pt-4">
              <div class="text-sm font-medium text-slate-800">单用户设置</div>
              <div class="grid grid-cols-1 gap-4 xl:grid-cols-2">
                <div>
                  <div class="mb-2 text-sm text-slate-600">选择用户</div>
                  <t-select
                    v-model="selectedUserId"
                    clearable
                    filterable
                    placeholder="请选择一个用户"
                    :options="userOptions"
                  />
                </div>
                <div>
                  <div class="mb-2 text-sm text-slate-600">分配角色</div>
                  <t-select
                    v-model="selectedUserRoleIds"
                    multiple
                    clearable
                    filterable
                    :min-collapsed-num="1"
                    placeholder="可多选角色"
                    :options="roleOptions"
                  />
                </div>
              </div>

              <div class="flex justify-end">
                <t-button
                  theme="primary"
                  :loading="assigningUserRoles"
                  :disabled="!selectedUserId || !canAssignUserRole"
                  @click="assignSelectedUserRoles"
                >
                  保存用户角色
                </t-button>
              </div>
            </section>

            <section class="space-y-4 border-t border-slate-200 pt-4">
              <div class="text-sm font-medium text-slate-800">批量追加角色</div>
              <div class="grid grid-cols-1 gap-4 xl:grid-cols-2">
                <div>
                  <div class="mb-2 text-sm text-slate-600">选择用户</div>
                  <t-select
                    v-model="batchUserIds"
                    multiple
                    clearable
                    filterable
                    :min-collapsed-num="1"
                    placeholder="可多选用户"
                    :options="userOptions"
                  />
                </div>
                <div>
                  <div class="mb-2 text-sm text-slate-600">追加角色</div>
                  <t-select
                    v-model="batchRoleIds"
                    multiple
                    clearable
                    filterable
                    :min-collapsed-num="1"
                    placeholder="可多选角色"
                    :options="roleOptions"
                  />
                </div>
              </div>

              <div class="flex justify-end">
                <t-button
                  theme="primary"
                  :loading="batchAppendingRoles"
                  :disabled="
                    batchUserIds.length === 0 || batchRoleIds.length === 0 || !canAssignUserRole
                  "
                  @click="batchAppendRoles"
                >
                  批量追加角色
                </t-button>
              </div>
            </section>

            <section class="space-y-3 border-t border-slate-200 pt-4">
              <div class="text-sm font-medium text-slate-800">当前用户角色</div>
              <div v-if="users.length === 0" class="text-sm text-slate-400">暂无用户</div>
              <div v-else class="divide-y divide-slate-200/80">
                <div v-for="user in users" :key="user.id" class="px-1 py-3">
                  <div class="flex items-start justify-between gap-3">
                    <div class="min-w-0">
                      <div class="flex flex-wrap items-center gap-2">
                        <span class="font-medium text-slate-800">{{ user.username }}</span>
                        <span v-if="user.nickname" class="text-sm text-slate-500">
                          {{ user.nickname }}
                        </span>
                        <t-tag
                          :theme="user.status === 1 ? 'success' : 'warning'"
                          size="small"
                          variant="light-outline"
                        >
                          {{ user.status === 1 ? "正常" : "禁用" }}
                        </t-tag>
                      </div>
                      <div class="mt-2 flex flex-wrap gap-2">
                        <t-tag
                          v-for="roleId in user.roleIds"
                          :key="`${user.id}-${roleId}`"
                          size="small"
                          variant="light-outline"
                        >
                          {{ roleLabelMap.get(roleId) || `角色#${roleId}` }}
                        </t-tag>
                        <span v-if="!user.roleIds?.length" class="text-xs text-slate-400">
                          未分配角色
                        </span>
                      </div>
                    </div>
                    <t-button
                      variant="outline"
                      size="small"
                      @click="bringUserToSingleAssign(user.id)"
                    >
                      带入单用户设置
                    </t-button>
                  </div>
                </div>
              </div>
            </section>
          </div>
        </t-loading>
      </t-drawer>

      <t-dialog
        v-model:visible="roleEditorVisible"
        :header="roleEditorTitle"
        width="560px"
        :confirm-btn="roleEditorMode === 'create' ? '创建角色' : '创建副本'"
        :confirm-loading="roleEditorLoading"
        @confirm="submitRoleEditor"
      >
        <div class="grid grid-cols-1 gap-4 py-2">
          <div>
            <div class="mb-2 text-sm text-slate-700">角色名称</div>
            <t-input v-model="roleEditorForm.roleName" clearable placeholder="例如：财务总监" />
          </div>

          <div>
            <div class="mb-2 text-sm text-slate-700">角色标识</div>
            <t-input
              v-model="roleEditorForm.roleKey"
              clearable
              placeholder="例如：finance_director"
            />
          </div>

          <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div>
              <div class="mb-2 text-sm text-slate-700">排序值</div>
              <t-input v-model="roleEditorForm.roleSort" type="number" placeholder="默认 0" />
            </div>
            <div>
              <div class="mb-2 text-sm text-slate-700">状态</div>
              <t-radio-group v-model="roleEditorForm.status" variant="outline">
                <t-radio :value="1">启用</t-radio>
                <t-radio :value="0">停用</t-radio>
              </t-radio-group>
            </div>
          </div>

          <div>
            <div class="mb-2 text-sm text-slate-700">角色说明</div>
            <t-textarea
              v-model="roleEditorForm.remark"
              :autosize="{ minRows: 3, maxRows: 5 }"
              maxlength="200"
              placeholder="描述该角色的职责范围或权限边界"
            />
          </div>
        </div>
      </t-dialog>
    </div>
  </PageFrame>
</template>

<script setup lang="ts">
import type {
  FormInstanceFunctions,
  FormRule,
  FormRules,
  SubmitContext,
  TreeProps,
} from "tdesign-vue-next";
import { MessagePlugin } from "tdesign-vue-next";
import { computed, onMounted, reactive, ref, watch } from "vue";
import { rbacApi, type RbacRole, type RbacUser } from "@/api/rbac";
import {
  newPermissionApi,
  type NewPermissionPublishResult,
  type NewPermissionResourceSaveParam,
  type NewPermissionRolePreview,
} from "@/api/new-permission";
import { PermissionConstant } from "@/constant/PermissionConstant";
import { usePermission } from "@/hooks/usePermission";
import useNewPermission from "@/hooks/useNewPermission";
import { getNewPageRegistryItem, listNewPageRegistry } from "@/router/pageRegistry";
import { clearNewPermissionCache, useNewPermissionStore } from "@/store/new-permission";
import type { NewPermissionResource, NewPermissionResourceType } from "@/types/router";
import { useRouter } from "vue-router";
import PageFrame from "@/components/PageFrame.vue";
import PageHeader from "@/components/PageHeader.vue";

defineOptions({
  name: "PermissionManageNew",
});

interface ResourceFormData {
  id?: number;
  resourceKey: string;
  resourceType: NewPermissionResourceType;
  parentResourceId?: number;
  pageKey: string;
  title: string;
  icon: string;
  sortOrder: number;
  hidden: boolean;
  permissionCode: string;
  buttonGroup: string;
  remark: string;
  status: number;
}

interface RoleEditorFormData {
  roleName: string;
  roleKey: string;
  roleSort: number;
  status: number;
  remark: string;
}

interface TreeNodeItem extends NewPermissionResource {
  value: number;
  label: string;
  children?: TreeNodeItem[];
}

interface PageButtonGroupSummary {
  group: string;
  buttons: NewPermissionResource[];
}

interface PermissionMatrixAction {
  id: number;
  label: string;
}

interface PermissionMatrixRow {
  key: string;
  title: string;
  hint: string;
  level: number;
  actions: PermissionMatrixAction[];
  parentResourceId?: number;
}

interface PermissionMatrixModule {
  id: number;
  title: string;
  hint: string;
  resourceIds: number[];
  rows: PermissionMatrixRow[];
  checked: boolean;
  indeterminate: boolean;
}

const { hasRole } = usePermission();
const { hasButtonPermission } = useNewPermission();
const router = useRouter();
const newPermissionStore = useNewPermissionStore();
const isAdmin = computed(() => hasRole("admin"));
const hasWorkbenchPermission = (permissionCode: string) =>
  isAdmin.value || hasButtonPermission(permissionCode);
const canReadResource = computed(() =>
  hasWorkbenchPermission(PermissionConstant.PERMISSION_RESOURCE_READ)
);
const canReadRoleAuth = computed(() =>
  hasWorkbenchPermission(PermissionConstant.PERMISSION_ROLE_AUTH_READ)
);
const canSaveResource = computed(() =>
  hasWorkbenchPermission(PermissionConstant.PERMISSION_RESOURCE_SAVE)
);
const canCreateRole = computed(() =>
  hasWorkbenchPermission(PermissionConstant.PERMISSION_ROLE_CREATE)
);
const canDeleteRole = computed(() =>
  hasWorkbenchPermission(PermissionConstant.PERMISSION_ROLE_DELETE)
);
const canCopyRole = computed(() => hasWorkbenchPermission(PermissionConstant.PERMISSION_ROLE_COPY));
const canSaveRoleAuth = computed(() =>
  hasWorkbenchPermission(PermissionConstant.PERMISSION_ROLE_AUTH_SAVE)
);
const canAssignUserRole = computed(() =>
  hasWorkbenchPermission(PermissionConstant.PERMISSION_USER_ASSIGN)
);
const canPreviewRole = computed(() =>
  hasWorkbenchPermission(PermissionConstant.PERMISSION_ROLE_PREVIEW)
);
const canValidatePublish = computed(() =>
  hasWorkbenchPermission(PermissionConstant.PERMISSION_PUBLISH_VALIDATE)
);
const canPublish = computed(() => hasWorkbenchPermission(PermissionConstant.PERMISSION_PUBLISH));

const treeKeys: TreeProps["keys"] = {
  value: "value",
  label: "label",
  children: "children",
};

type PermissionTab = "MENU" | "ACTION" | "DATA" | "USER";

const keyword = ref("");
const roleKeyword = ref("");
const activeTab = ref<PermissionTab>("MENU");

const permissionTabs: PermissionTab[] = ["MENU", "ACTION", "DATA", "USER"];

const tabLabelMap: Record<PermissionTab, string> = {
  MENU: "菜单权限",
  ACTION: "操作权限",
  DATA: "数据权限",
  USER: "成员用户",
};

const resourceTypeFilter = ref<NewPermissionResourceType | undefined>();
const resourcesLoading = ref(false);
const detailLoading = ref(false);
const savingResource = ref(false);
const roleResourceLoading = ref(false);
const savingRoleResources = ref(false);
const publishValidating = ref(false);
const publishing = ref(false);
const previewLoading = ref(false);
const usersLoading = ref(false);
const assigningUserRoles = ref(false);
const batchAppendingRoles = ref(false);
const roleEditorLoading = ref(false);

const previewDrawerVisible = ref(false);
const validateDrawerVisible = ref(false);
const userRoleDrawerVisible = ref(false);
const resourceDrawerVisible = ref(false);
const roleEditorVisible = ref(false);
const roleEditorMode = ref<"create" | "copy">("create");
const publishSummary = ref("");

const resources = ref<NewPermissionResource[]>([]);
const flatResources = ref<NewPermissionResource[]>([]);
const roles = ref<RbacRole[]>([]);
const users = ref<RbacUser[]>([]);
const selectedRoleId = ref<number | undefined>();
const previewData = ref<NewPermissionRolePreview | null>(null);
const validateResult = ref<NewPermissionPublishResult | null>(null);
const selectedResourceId = ref<number | undefined>();
const selectedUserId = ref<number | undefined>();
const selectedUserRoleIds = ref<number[]>([]);
const batchUserIds = ref<number[]>([]);
const batchRoleIds = ref<number[]>([]);

const roleResourceDraftMap = reactive<Record<number, number[]>>({});
const roleResourceBaselineMap = reactive<Record<number, number[]>>({});
const collapsedTreePermissionModuleIds = ref<
  Record<Extract<PermissionTab, "MENU" | "DATA">, number[]>
>({
  MENU: [],
  DATA: [],
});

const resourceFormRef = ref<FormInstanceFunctions>();
const formData = reactive<ResourceFormData>(createEmptyForm());
const roleEditorForm = reactive<RoleEditorFormData>(createEmptyRoleForm());

const pageKeyRule: FormRule = {
  validator: (value: unknown) => {
    if (formData.resourceType !== "PAGE") {
      return true;
    }
    return Boolean(String(value || "").trim());
  },
  message: "页面资源必须绑定 pageKey",
  trigger: "submit",
};

const permissionCodeRule: FormRule = {
  validator: (value: unknown) => {
    if (formData.resourceType !== "BUTTON") {
      return true;
    }
    return Boolean(String(value || "").trim());
  },
  message: "按钮资源必须填写权限码",
  trigger: "submit",
};

const formRules: FormRules<ResourceFormData> = {
  resourceType: [{ required: true, message: "请选择资源类型", trigger: "change" }],
  resourceKey: [{ required: true, message: "请输入资源唯一键", trigger: "blur" }],
  title: [{ required: true, message: "请输入资源标题", trigger: "blur" }],
  pageKey: [pageKeyRule],
  permissionCode: [permissionCodeRule],
};

const pageOptions = computed(() =>
  listNewPageRegistry().map((item) => ({
    label: `${item.meta.title} (${item.pageKey})`,
    value: item.pageKey,
  }))
);

const treeData = computed<TreeNodeItem[]>(() => buildTreeNodes(resources.value));

const filteredTreeData = computed<TreeNodeItem[]>(() =>
  filterTreeNodes(treeData.value, keyword.value.trim(), resourceTypeFilter.value)
);

const filteredResourceCount = computed(() => normalizeTree(filteredTreeData.value).length);

const activedTreeValues = computed(() =>
  selectedResourceId.value ? [selectedResourceId.value] : []
);

const selectedResource = computed(() =>
  flatResources.value.find((item) => item.id === selectedResourceId.value)
);

const selectedRole = computed(() =>
  roles.value.find((item) => item.roleId === selectedRoleId.value)
);

const roleMemberCountMap = computed(() => {
  const counter = new Map<number, number>();
  roles.value.forEach((item) => counter.set(item.roleId, 0));
  users.value.forEach((user) => {
    (user.roleIds || []).forEach((roleId) => {
      counter.set(roleId, (counter.get(roleId) || 0) + 1);
    });
  });
  return counter;
});

const filteredRoles = computed(() => {
  const keywordText = roleKeyword.value.trim().toLowerCase();
  return [...roles.value]
    .sort((left, right) => (left.roleSort || 0) - (right.roleSort || 0))
    .filter((item) => {
      if (!keywordText) {
        return true;
      }
      return `${item.roleName} ${item.roleKey}`.toLowerCase().includes(keywordText);
    });
});

const selectedRoleLocked = computed(() =>
  Boolean(selectedRole.value && isSystemRole(selectedRole.value))
);

const rolePermissionLocked = computed(
  () => !canSaveRoleAuth.value || !selectedRoleId.value || selectedRoleLocked.value
);

const selectedRoleResourceIds = computed(() =>
  selectedRoleId.value ? listRoleResourceIds(roleResourceDraftMap, selectedRoleId.value) : []
);

const selectedRoleAssignedCount = computed(() => selectedRoleResourceIds.value.length);

const selectedRoleDirty = computed(() =>
  Boolean(selectedRoleId.value && isRoleDirty(selectedRoleId.value))
);

const canDeleteSelectedRole = computed(() =>
  Boolean(selectedRole.value && !isSystemRole(selectedRole.value))
);

const selectedRoleDescription = computed(() => {
  if (!selectedRole.value) {
    return "请从左侧选择一个角色后继续配置权限。";
  }
  return (
    selectedRole.value.remark?.trim() ||
    `该角色当前已配置 ${selectedRoleAssignedCount.value} 项资源授权，可在当前工作区继续调整页面、菜单与按钮权限。`
  );
});

const menuPermissionModules = computed<PermissionMatrixModule[]>(() =>
  buildMenuPermissionModules(resources.value, selectedRoleResourceIds.value)
);

const permissionModules = computed<PermissionMatrixModule[]>(() =>
  buildPermissionModules(resources.value, selectedRoleResourceIds.value)
);

const dataPermissionModules = computed<PermissionMatrixModule[]>(() =>
  buildDataPermissionModules()
);

const isTreePermissionTab = computed(
  () => activeTab.value === "MENU" || activeTab.value === "DATA"
);

const activeTreePermissionModules = computed<PermissionMatrixModule[]>(() => {
  if (activeTab.value === "DATA") {
    return dataPermissionModules.value;
  }
  return menuPermissionModules.value;
});

const userPermissionModules = computed<PermissionMatrixModule[]>(() =>
  buildPlaceholderPermissionModules()
);

const activePermissionModules = computed<PermissionMatrixModule[]>(() => {
  if (activeTab.value === "MENU") {
    return menuPermissionModules.value;
  }
  if (activeTab.value === "ACTION") {
    return permissionModules.value;
  }
  if (activeTab.value === "DATA") {
    return dataPermissionModules.value;
  }
  return userPermissionModules.value;
});

const activePermissionEmptyText = computed(() => {
  if (activeTab.value === "MENU") {
    return "当前暂无页面或菜单资源，请先在资源工作台维护。";
  }
  if (activeTab.value === "ACTION") {
    return "当前暂无可配置资源，请先在资源工作台维护。";
  }
  return "当前模块暂无可配置项，后续接入真实数据后可在此维护。";
});

const activeModuleCheckAllText = computed(() => {
  if (activeTab.value === "MENU") {
    return "全选本页菜单";
  }
  return "全选本模块";
});

const activeTreeModuleCheckAllText = computed(() => {
  if (activeTab.value === "DATA") {
    return "全选本组数据权限";
  }
  return "全选本组菜单";
});

const activeTreeEmptyRowText = computed(() => {
  if (activeTab.value === "DATA") {
    return "暂无可配置数据权限";
  }
  return "暂无可配置菜单";
});

const isEditingResource = computed(
  () => Boolean(selectedResource.value) || Boolean(formData.resourceKey)
);

const currentPageRegistry = computed(() => getNewPageRegistryItem(formData.pageKey));

const currentParentResourceTitle = computed(() => {
  if (!formData.parentResourceId) {
    return "根节点";
  }
  return (
    flatResources.value.find((item) => item.id === formData.parentResourceId)?.title ||
    "未匹配父级资源"
  );
});

const currentBindingLabel = computed(() => {
  if (formData.resourceType === "BUTTON") {
    return "权限码";
  }
  if (formData.resourceType === "MENU") {
    return formData.pageKey ? "页面入口" : "目录菜单";
  }
  return "页面绑定";
});

const currentBindingValue = computed(() => {
  if (formData.resourceType === "BUTTON") {
    return formData.permissionCode?.trim() || "未配置权限码";
  }
  if (!formData.pageKey) {
    return formData.resourceType === "MENU" ? "目录型菜单" : "未绑定 pageKey";
  }
  if (!currentPageRegistry.value) {
    return `${formData.pageKey}（未命中注册表）`;
  }
  return `${currentPageRegistry.value.meta.title} /new/${currentPageRegistry.value.path}`;
});

const currentVisibilityText = computed(
  () => `${formData.status === 1 ? "启用" : "停用"} · ${formData.hidden ? "隐藏" : "显示"}`
);

const currentEditorModeText = computed(() => (formData.id ? "编辑已有资源" : "新增草稿资源"));

const parentResourceOptions = computed(() => {
  const selectedId = formData.id;
  return flatResources.value
    .filter((item) => item.id !== selectedId)
    .filter((item) => item.resourceType !== "BUTTON")
    .map((item) => ({
      label: `${resourceTypeLabel(item.resourceType)} · ${item.title}`,
      value: item.id,
    }));
});

const roleOptions = computed(() =>
  roles.value.map((item) => ({
    label: `${item.roleName} (${item.roleKey})`,
    value: item.roleId,
  }))
);

const userOptions = computed(() =>
  users.value.map((item) => ({
    label: `${item.username}${item.nickname ? ` (${item.nickname})` : ""}`,
    value: item.id,
  }))
);

const roleLabelMap = computed(
  () => new Map(roles.value.map((item) => [item.roleId, item.roleName]))
);

const selectedPageDescendants = computed(() => {
  if (selectedResource.value?.resourceType !== "PAGE") {
    return [];
  }
  return collectDescendantResources(selectedResource.value);
});

const selectedPageMenus = computed(() =>
  selectedPageDescendants.value.filter((item) => item.resourceType === "MENU")
);

const selectedPageButtons = computed(() =>
  selectedPageDescendants.value.filter((item) => item.resourceType === "BUTTON")
);

const selectedPageButtonGroups = computed<PageButtonGroupSummary[]>(() => {
  const groups = new Map<string, NewPermissionResource[]>();
  selectedPageButtons.value.forEach((item) => {
    const groupKey = item.buttonGroup?.trim() || "未分组";
    const current = groups.get(groupKey) || [];
    current.push(item);
    groups.set(groupKey, current);
  });

  return Array.from(groups.entries()).map(([group, buttons]) => ({
    group,
    buttons,
  }));
});

const previewNavigationTreeData = computed<TreeNodeItem[]>(() =>
  buildTreeNodes(previewData.value?.navigationTree || [])
);

const dirtySavableRoleIds = computed(() =>
  roles.value
    .filter((item) => !isSystemRole(item) && isRoleDirty(item.roleId))
    .map((item) => item.roleId)
);

const roleEditorTitle = computed(() =>
  roleEditorMode.value === "create" ? "新增角色" : "复制角色"
);

const pageInfoText = computed(() => {
  if (formData.resourceType === "MENU" && !formData.pageKey) {
    return "当前菜单为目录型菜单，只作为导航分组展示，不直接绑定页面组件。";
  }
  const page = currentPageRegistry.value;
  if (!page) {
    return "当前 pageKey 未在前端注册表中命中，发布后该节点会被前端跳过。";
  }
  return `页面来源：${page.meta.title}，路径 /new/${page.path}，路由名 ${page.routeName}。路径和组件映射由前端注册表维护，不在此页编辑。`;
});

watch(
  () => formData.resourceType,
  (value) => {
    if (value === "BUTTON") {
      formData.pageKey = "";
      formData.icon = "";
      return;
    }

    if (value === "PAGE" && !formData.pageKey) {
      formData.pageKey = defaultPageKey();
    }
  }
);

watch(selectedUserId, (value) => {
  syncSelectedUserRoleIds(value);
});

onMounted(async () => {
  await newPermissionStore.loadButtonPermissions();

  if (!canReadResource.value || !canReadRoleAuth.value) {
    MessagePlugin.warning("当前账号缺少新权限工作台读取权限");
    router.push("/");
    return;
  }

  await Promise.all([loadResources(), loadRoles(), loadUsers()]);
});

function defaultPageKey(): string {
  return listNewPageRegistry()[0]?.pageKey || "";
}

function createEmptyForm(resourceType: NewPermissionResourceType = "PAGE"): ResourceFormData {
  return {
    resourceType,
    resourceKey: "",
    title: "",
    pageKey: resourceType === "PAGE" ? defaultPageKey() : "",
    icon: "",
    sortOrder: 0,
    hidden: false,
    permissionCode: "",
    buttonGroup: "",
    remark: "",
    status: 1,
  };
}

function createEmptyRoleForm(source?: Partial<RbacRole>): RoleEditorFormData {
  return {
    roleName: source?.roleName || "",
    roleKey: source?.roleKey || "",
    roleSort: source?.roleSort ?? roles.value.length + 1,
    status: source?.status ?? 1,
    remark: source?.remark || "",
  };
}

function normalizeTree(resourceList: NewPermissionResource[]): NewPermissionResource[] {
  const result: NewPermissionResource[] = [];
  const visit = (items: NewPermissionResource[]) => {
    items.forEach((item) => {
      result.push(item);
      if (item.children?.length) {
        visit(item.children);
      }
    });
  };
  visit(resourceList);
  return result;
}

function buildTreeNodes(resourceList: NewPermissionResource[]): TreeNodeItem[] {
  return resourceList.map((item) => ({
    ...item,
    value: item.id,
    label: item.title,
    children: buildTreeNodes(item.children || []),
  }));
}

function filterTreeNodes(
  nodes: TreeNodeItem[],
  searchKeyword: string,
  typeFilter?: NewPermissionResourceType
): TreeNodeItem[] {
  return nodes
    .map((node) => ({
      ...node,
      children: filterTreeNodes(node.children || [], searchKeyword, typeFilter),
    }))
    .filter((node) => {
      const matchKeyword =
        !searchKeyword ||
        [node.title, node.resourceKey, node.permissionCode]
          .filter(Boolean)
          .some((value) => String(value).toLowerCase().includes(searchKeyword.toLowerCase()));
      const matchType = !typeFilter || node.resourceType === typeFilter;
      return (matchKeyword && matchType) || (node.children?.length || 0) > 0;
    });
}

function resourceTypeLabel(type?: NewPermissionResourceType) {
  if (type === "PAGE") {
    return "页面";
  }
  if (type === "MENU") {
    return "菜单";
  }
  if (type === "BUTTON") {
    return "按钮";
  }
  return "未知";
}

function resourceTypeTheme(type?: NewPermissionResourceType) {
  if (type === "PAGE") {
    return "primary";
  }
  if (type === "MENU") {
    return "success";
  }
  if (type === "BUTTON") {
    return "warning";
  }
  return "default";
}

function collectDescendantResources(resource: NewPermissionResource): NewPermissionResource[] {
  const result: NewPermissionResource[] = [];
  const visit = (items: NewPermissionResource[]) => {
    items.forEach((item) => {
      result.push(item);
      if (item.children?.length) {
        visit(item.children);
      }
    });
  };
  visit(resource.children || []);
  return result;
}

function normalizeIdList(ids: number[]): number[] {
  return Array.from(new Set(ids)).sort((a, b) => a - b);
}

function listRoleResourceIds(source: Record<number, number[]>, roleId: number): number[] {
  return Array.isArray(source[roleId]) ? source[roleId] : [];
}

function hasRoleResource(
  source: Record<number, number[]>,
  roleId: number,
  resourceId?: number
): boolean {
  if (!resourceId) {
    return false;
  }
  return listRoleResourceIds(source, roleId).includes(resourceId);
}

function isSameIdList(left: number[], right: number[]): boolean {
  const nextLeft = normalizeIdList(left);
  const nextRight = normalizeIdList(right);
  if (nextLeft.length !== nextRight.length) {
    return false;
  }
  return nextLeft.every((item, index) => item === nextRight[index]);
}

function isRoleDirty(roleId: number): boolean {
  return !isSameIdList(
    listRoleResourceIds(roleResourceDraftMap, roleId),
    listRoleResourceIds(roleResourceBaselineMap, roleId)
  );
}

function setRoleResourceIds(target: Record<number, number[]>, roleId: number, ids: number[]) {
  target[roleId] = normalizeIdList(ids);
}

function resetRoleResourceMap(target: Record<number, number[]>) {
  Object.keys(target).forEach((key) => {
    delete target[Number(key)];
  });
}

function syncSelectedUserRoleIds(userId?: number) {
  const currentUser = users.value.find((item) => item.id === userId);
  selectedUserRoleIds.value = currentUser ? [...currentUser.roleIds] : [];
}

function isSystemRole(role: RbacRole) {
  return role.roleKey === "admin";
}

function allResourceIds(): number[] {
  return flatResources.value.map((item) => item.id);
}

function ensureAdminRoleResources() {
  const resourceIds = allResourceIds();
  roles.value.filter(isSystemRole).forEach((role) => {
    setRoleResourceIds(roleResourceDraftMap, role.roleId, resourceIds);
    setRoleResourceIds(roleResourceBaselineMap, role.roleId, resourceIds);
  });
}

function roleListDescription(role: RbacRole) {
  const remark = role.remark?.trim();
  if (remark) {
    return remark;
  }
  const resourceCount = listRoleResourceIds(roleResourceDraftMap, role.roleId).length;
  return resourceCount > 0 ? `已配置 ${resourceCount} 项资源权限` : "尚未配置权限说明";
}

function buildMenuPermissionModules(
  resourceList: NewPermissionResource[],
  assignedIds: number[]
): PermissionMatrixModule[] {
  const assignedSet = new Set(assignedIds);
  const modules = resourceList
    .filter((resource) => resource.resourceType === "MENU")
    .map((resource) => buildParentMenuPermissionModule(resource, assignedSet));

  const groupedIds = new Set(modules.flatMap((module) => module.resourceIds));
  const ungroupedRows = normalizeTree(resourceList)
    .filter((resource) => resource.resourceType === "PAGE" && !groupedIds.has(resource.id))
    .map((resource) => buildMenuPermissionRow(resource, 1));

  if (ungroupedRows.length > 0) {
    const resourceIds = ungroupedRows.flatMap((row) => row.actions.map((action) => action.id));
    const checkedCount = resourceIds.filter((id) => assignedSet.has(id)).length;
    modules.push({
      id: -100,
      title: "未分组菜单",
      hint: "未挂载到父菜单的页面入口",
      resourceIds,
      rows: ungroupedRows,
      checked: resourceIds.length > 0 && checkedCount === resourceIds.length,
      indeterminate: checkedCount > 0 && checkedCount < resourceIds.length,
    });
  }

  return modules;
}

function buildDataPermissionModules(): PermissionMatrixModule[] {
  return [
    {
      id: -10,
      title: "数据范围",
      hint: "按业务对象控制可见数据边界",
      resourceIds: [],
      rows: [
        { key: "data-scope-own", title: "本人数据", hint: "", level: 1, actions: [] },
        { key: "data-scope-team", title: "团队数据", hint: "", level: 1, actions: [] },
        { key: "data-scope-all", title: "全部数据", hint: "", level: 1, actions: [] },
      ],
      checked: false,
      indeterminate: false,
    },
    {
      id: -11,
      title: "字段权限",
      hint: "按字段控制查看与编辑边界",
      resourceIds: [],
      rows: [
        { key: "data-field-view", title: "字段查看", hint: "", level: 1, actions: [] },
        { key: "data-field-edit", title: "字段编辑", hint: "", level: 1, actions: [] },
      ],
      checked: false,
      indeterminate: false,
    },
  ];
}

function buildPlaceholderPermissionModules() {
  return [
    {
      id: -2,
      title: "成员用户模块",
      hint: "后续接入真实数据",
      resourceIds: [],
      rows: [
        { key: "member-list", title: "成员列表", hint: "", level: 0, actions: [] },
        { key: "member-role", title: "角色成员", hint: "", level: 0, actions: [] },
      ],
      checked: false,
      indeterminate: false,
    },
  ];
}

function buildPermissionModules(
  resourceList: NewPermissionResource[],
  assignedIds: number[]
): PermissionMatrixModule[] {
  const assignedSet = new Set(assignedIds);
  const modules: PermissionMatrixModule[] = [];

  resourceList.forEach((resource) => {
    const resourceIds = [
      resource.id,
      ...collectDescendantResources(resource).map((item) => item.id),
    ];
    const checkedCount = resourceIds.filter((id) => assignedSet.has(id)).length;

    modules.push({
      id: resource.id,
      title: resource.title,
      hint: resource.pageKey || resource.resourceKey || "",
      resourceIds,
      rows: buildPermissionMatrixRows(resource, true),
      checked: resourceIds.length > 0 && checkedCount === resourceIds.length,
      indeterminate: checkedCount > 0 && checkedCount < resourceIds.length,
    });
  });

  return modules;
}

function buildParentMenuPermissionModule(
  resource: NewPermissionResource,
  assignedSet: Set<number>
): PermissionMatrixModule {
  const rows = collectMenuRows(resource.children || [], 1, resource.id);
  const rowResourceIds = rows.flatMap((row) => row.actions.map((action) => action.id));
  const resourceIds = [resource.id, ...rowResourceIds];
  const checkedCount = resourceIds.filter((id) => assignedSet.has(id)).length;

  return {
    id: resource.id,
    title: resource.title,
    hint: resource.pageKey || resource.resourceKey || "",
    resourceIds,
    rows,
    checked: resourceIds.length > 0 && checkedCount === resourceIds.length,
    indeterminate: checkedCount > 0 && checkedCount < resourceIds.length,
  };
}

function collectMenuRows(
  items: NewPermissionResource[],
  level: number,
  parentResourceId: number
): PermissionMatrixRow[] {
  return items
    .filter((item) => item.resourceType !== "BUTTON")
    .flatMap((item) => {
      const rows: PermissionMatrixRow[] = [buildMenuPermissionRow(item, level, parentResourceId)];
      rows.push(...collectMenuRows(item.children || [], level + 1, item.id));
      return rows;
    });
}

function buildMenuPermissionRow(
  resource: NewPermissionResource,
  level: number,
  parentResourceId?: number
): PermissionMatrixRow {
  return {
    key: `menu-resource-${resource.id}`,
    title: resource.title,
    hint: resource.pageKey || resource.resourceKey,
    level,
    parentResourceId,
    actions: [{ id: resource.id, label: "可见" }],
  };
}

function buildPermissionMatrixRows(
  resource: NewPermissionResource,
  isRoot = false
): PermissionMatrixRow[] {
  const childResources = resource.children || [];
  const nonButtonChildren = childResources.filter((item) => item.resourceType !== "BUTTON");
  const buttonChildren = childResources.filter((item) => item.resourceType === "BUTTON");
  const rows: PermissionMatrixRow[] = [];

  const actions: PermissionMatrixAction[] = [
    { id: resource.id, label: resource.resourceType === "BUTTON" ? resource.title : "查看" },
  ];
  buttonChildren.forEach((item) => {
    actions.push({ id: item.id, label: item.title });
  });

  const title = isRoot ? "页面入口" : resource.title;
  const hint =
    resource.resourceType === "BUTTON"
      ? resource.permissionCode || resource.resourceKey
      : resource.pageKey || resource.resourceKey;
  rows.push({
    key: `resource-${resource.id}`,
    title,
    hint,
    level: 0,
    actions,
  });

  nonButtonChildren.forEach((item) => {
    rows.push(...buildPermissionMatrixRows(item));
  });

  return rows;
}

function isSelectedRoleResource(resourceId: number) {
  if (!selectedRoleId.value) {
    return false;
  }
  return hasRoleResource(roleResourceDraftMap, selectedRoleId.value, resourceId);
}

function updateSelectedRoleResources(nextIds: number[]) {
  if (!selectedRoleId.value) {
    return;
  }
  setRoleResourceIds(roleResourceDraftMap, selectedRoleId.value, nextIds);
}

function toggleSelectedRolePermission(resourceId: number, enabled: string | number | boolean) {
  if (rolePermissionLocked.value) {
    return;
  }
  const next = new Set(selectedRoleResourceIds.value);
  if (enabled) {
    next.add(resourceId);
  } else {
    next.delete(resourceId);
  }
  updateSelectedRoleResources(Array.from(next));
}

function toggleTreePermissionRow(
  row: PermissionMatrixRow,
  resourceId: number,
  enabled: string | number | boolean
) {
  if (rolePermissionLocked.value) {
    return;
  }
  const next = new Set(selectedRoleResourceIds.value);
  if (enabled) {
    if (row.parentResourceId) {
      next.add(row.parentResourceId);
    }
    next.add(resourceId);
  } else {
    next.delete(resourceId);
  }
  updateSelectedRoleResources(Array.from(next));
}

function currentTreePermissionTab() {
  return activeTab.value === "DATA" ? "DATA" : "MENU";
}

function isTreePermissionModuleCollapsed(moduleId: number) {
  return collapsedTreePermissionModuleIds.value[currentTreePermissionTab()].includes(moduleId);
}

function toggleTreePermissionModuleCollapsed(moduleId: number) {
  const tab = currentTreePermissionTab();
  const collapsedIds = collapsedTreePermissionModuleIds.value[tab];
  if (collapsedIds.includes(moduleId)) {
    collapsedTreePermissionModuleIds.value = {
      ...collapsedTreePermissionModuleIds.value,
      [tab]: collapsedIds.filter((id) => id !== moduleId),
    };
    return;
  }
  collapsedTreePermissionModuleIds.value = {
    ...collapsedTreePermissionModuleIds.value,
    [tab]: [...collapsedIds, moduleId],
  };
}

function toggleModuleResources(module: PermissionMatrixModule, enabled: string | number | boolean) {
  if (rolePermissionLocked.value) {
    return;
  }

  const next = new Set(selectedRoleResourceIds.value);
  module.resourceIds.forEach((id) => {
    if (enabled) {
      next.add(id);
    } else {
      next.delete(id);
    }
  });
  updateSelectedRoleResources(Array.from(next));
}

function resetSelectedRoleDraft() {
  if (!selectedRoleId.value || selectedRoleLocked.value) {
    return;
  }
  setRoleResourceIds(
    roleResourceDraftMap,
    selectedRoleId.value,
    listRoleResourceIds(roleResourceBaselineMap, selectedRoleId.value)
  );
}

async function reloadWorkbench() {
  await Promise.all([loadResources(true), loadRoles(), loadUsers()]);
}

async function loadResources(preserveSelection = false) {
  resourcesLoading.value = true;
  try {
    const data = await newPermissionApi.listDraftResources();
    resources.value = data;
    flatResources.value = normalizeTree(data);

    const currentExists = selectedResourceId.value
      ? flatResources.value.some((item) => item.id === selectedResourceId.value)
      : false;

    if (!preserveSelection || !currentExists) {
      selectedResourceId.value = flatResources.value[0]?.id;
    }

    if (selectedResourceId.value) {
      await selectResource(selectedResourceId.value);
    } else {
      Object.assign(formData, createEmptyForm());
    }
    ensureAdminRoleResources();
  } finally {
    resourcesLoading.value = false;
  }
}

async function loadRoles() {
  roles.value = await rbacApi.listRoles();
  if (!roles.value.some((item) => item.roleId === selectedRoleId.value)) {
    selectedRoleId.value = roles.value[0]?.roleId;
  }
  await loadAllRoleResources();
}

async function loadAllRoleResources() {
  roleResourceLoading.value = true;
  try {
    resetRoleResourceMap(roleResourceDraftMap);
    resetRoleResourceMap(roleResourceBaselineMap);

    const snapshots = await Promise.all(
      roles.value.map(async (item) => ({
        roleId: item.roleId,
        resourceIds: await newPermissionApi.listDraftRoleResourceIds(item.roleId),
      }))
    );

    snapshots.forEach((item) => {
      setRoleResourceIds(roleResourceDraftMap, item.roleId, item.resourceIds);
      setRoleResourceIds(roleResourceBaselineMap, item.roleId, item.resourceIds);
    });
    ensureAdminRoleResources();
  } finally {
    roleResourceLoading.value = false;
  }
}

async function loadUsers() {
  usersLoading.value = true;
  try {
    users.value = await rbacApi.listUsers();
    if (selectedUserId.value && !users.value.some((item) => item.id === selectedUserId.value)) {
      selectedUserId.value = undefined;
      return;
    }
    syncSelectedUserRoleIds(selectedUserId.value);
  } finally {
    usersLoading.value = false;
  }
}

async function selectResource(id?: number) {
  if (!id) {
    selectedResourceId.value = undefined;
    Object.assign(formData, createEmptyForm());
    return;
  }

  selectedResourceId.value = id;
  detailLoading.value = true;
  try {
    const detail = await newPermissionApi.getDraftResource(id);
    Object.assign(formData, {
      id: detail.id,
      resourceKey: detail.resourceKey,
      resourceType: detail.resourceType,
      parentResourceId: detail.parentResourceId || undefined,
      pageKey: detail.pageKey || "",
      title: detail.title,
      icon: detail.icon || "",
      sortOrder: detail.sortOrder || 0,
      hidden: Boolean(detail.hidden),
      permissionCode: detail.permissionCode || "",
      buttonGroup: detail.buttonGroup || "",
      remark: detail.remark || "",
      status: detail.status ?? 1,
    });
  } finally {
    detailLoading.value = false;
  }
}

function openCreateResource(resourceType: NewPermissionResourceType = "PAGE") {
  if (!canSaveResource.value) {
    return;
  }
  selectedResourceId.value = undefined;
  Object.assign(formData, createEmptyForm(resourceType));
}

function resetSelectedResource() {
  if (selectedResourceId.value) {
    void selectResource(selectedResourceId.value);
    return;
  }
  Object.assign(formData, createEmptyForm(formData.resourceType));
}

function handleTreeActive(value: Array<string | number>) {
  const nextId = Number(value[0] || 0);
  if (!nextId) {
    return;
  }
  void selectResource(nextId);
}

async function handleSaveResource(context: SubmitContext) {
  if (!canSaveResource.value) {
    return;
  }

  if (context.validateResult !== true) {
    if (context.firstError) {
      MessagePlugin.warning(context.firstError);
    }
    return;
  }

  savingResource.value = true;
  try {
    const payload: NewPermissionResourceSaveParam = {
      id: formData.id,
      resourceKey: formData.resourceKey.trim(),
      resourceType: formData.resourceType,
      parentResourceId: formData.parentResourceId,
      pageKey: formData.resourceType === "BUTTON" ? "" : formData.pageKey.trim(),
      title: formData.title.trim(),
      icon: formData.resourceType === "BUTTON" ? "" : formData.icon.trim(),
      sortOrder: Number(formData.sortOrder || 0),
      hidden: Boolean(formData.hidden),
      permissionCode: formData.resourceType === "BUTTON" ? formData.permissionCode.trim() : "",
      buttonGroup: formData.resourceType === "BUTTON" ? formData.buttonGroup.trim() : "",
      remark: formData.remark.trim(),
      status: formData.status,
    };
    const saved = await newPermissionApi.saveDraftResource(payload);
    MessagePlugin.success("草稿资源已保存");
    await loadResources(true);
    await selectResource(saved.id);
  } finally {
    savingResource.value = false;
  }
}

async function saveRoleResources() {
  if (!canSaveRoleAuth.value) {
    return;
  }

  const changedRoleIds = [...dirtySavableRoleIds.value];
  if (changedRoleIds.length === 0) {
    MessagePlugin.warning("当前没有待保存的角色授权变更");
    return;
  }

  savingRoleResources.value = true;
  try {
    for (const roleId of changedRoleIds) {
      const resourceIds = listRoleResourceIds(roleResourceDraftMap, roleId);
      await newPermissionApi.saveDraftRoleResources(roleId, resourceIds);
      setRoleResourceIds(roleResourceBaselineMap, roleId, resourceIds);
    }
    MessagePlugin.success(`已保存 ${changedRoleIds.length} 个角色的授权草稿`);
  } finally {
    savingRoleResources.value = false;
  }
}

function openCreateRoleDialog() {
  if (!canCreateRole.value) {
    return;
  }
  roleEditorMode.value = "create";
  Object.assign(roleEditorForm, createEmptyRoleForm());
  roleEditorVisible.value = true;
}

function openCopyRoleDialog() {
  if (!canCopyRole.value || !selectedRole.value) {
    return;
  }
  roleEditorMode.value = "copy";
  Object.assign(
    roleEditorForm,
    createEmptyRoleForm({
      roleName: `${selectedRole.value.roleName}副本`,
      roleKey: `${selectedRole.value.roleKey}_copy`,
      roleSort: selectedRole.value.roleSort,
      status: selectedRole.value.status,
      remark: selectedRole.value.remark,
    })
  );
  roleEditorVisible.value = true;
}

async function submitRoleEditor() {
  if (roleEditorMode.value === "create" && !canCreateRole.value) {
    return;
  }
  if (roleEditorMode.value === "copy" && !canCopyRole.value) {
    return;
  }

  const roleName = roleEditorForm.roleName.trim();
  const roleKey = roleEditorForm.roleKey.trim();
  if (!roleName) {
    MessagePlugin.warning("请输入角色名称");
    return;
  }
  if (!roleKey) {
    MessagePlugin.warning("请输入角色标识");
    return;
  }

  const copySourceIds =
    roleEditorMode.value === "copy" && selectedRoleId.value
      ? [...listRoleResourceIds(roleResourceDraftMap, selectedRoleId.value)]
      : [];

  roleEditorLoading.value = true;
  try {
    await rbacApi.createRole({
      roleName,
      roleKey,
      roleSort: Number(roleEditorForm.roleSort || 0),
      status: roleEditorForm.status,
      remark: roleEditorForm.remark.trim(),
    });

    await loadRoles();
    const createdRole = roles.value.find((item) => item.roleKey === roleKey);

    if (createdRole && copySourceIds.length > 0) {
      await newPermissionApi.saveDraftRoleResources(createdRole.roleId, copySourceIds);
      await loadAllRoleResources();
    }

    if (createdRole) {
      selectedRoleId.value = createdRole.roleId;
    }

    roleEditorVisible.value = false;
    MessagePlugin.success(roleEditorMode.value === "create" ? "角色已创建" : "角色副本已创建");
  } finally {
    roleEditorLoading.value = false;
  }
}

async function handleDeleteSelectedRole() {
  if (!canDeleteRole.value || !selectedRole.value || !canDeleteSelectedRole.value) {
    return;
  }

  const confirmed = window.confirm(`确认删除角色“${selectedRole.value.roleName}”吗？`);
  if (!confirmed) {
    return;
  }

  await rbacApi.deleteRole(selectedRole.value.roleId);
  MessagePlugin.success("角色已删除");
  await Promise.all([loadRoles(), loadUsers()]);
}

async function openPreviewDrawer() {
  if (!canPreviewRole.value) {
    return;
  }
  if (!selectedRoleId.value) {
    MessagePlugin.warning("请先选择角色");
    return;
  }
  previewDrawerVisible.value = true;
  previewLoading.value = true;
  try {
    previewData.value = await newPermissionApi.previewRole(selectedRoleId.value);
  } finally {
    previewLoading.value = false;
  }
}

async function openValidateDrawer() {
  if (!canValidatePublish.value) {
    return;
  }
  validateDrawerVisible.value = true;
  publishValidating.value = true;
  try {
    validateResult.value = await newPermissionApi.validatePublish({
      remark: "PermissionManageNew 校验",
    });
  } finally {
    publishValidating.value = false;
  }
}

async function handlePublish() {
  if (!canPublish.value) {
    return;
  }
  publishing.value = true;
  try {
    const result = await newPermissionApi.publish({ remark: "PermissionManageNew 发布" });
    publishSummary.value = `发布成功，版本 ${result.configVersion}，生效时间 ${result.publishedAt}`;
    validateResult.value = result;
    clearNewPermissionCache();
    await newPermissionStore.loadNavigation(true);
    await newPermissionStore.loadButtonPermissions(true);
    MessagePlugin.success("新权限配置已发布");
  } finally {
    publishing.value = false;
  }
}

async function openUserRoleDrawer() {
  if (!canAssignUserRole.value) {
    return;
  }
  userRoleDrawerVisible.value = true;
  await loadUsers();
}

function bringUserToSingleAssign(userId: number) {
  userRoleDrawerVisible.value = true;
  selectedUserId.value = userId;
}

async function assignSelectedUserRoles() {
  if (!canAssignUserRole.value) {
    return;
  }
  if (!selectedUserId.value) {
    MessagePlugin.warning("请先选择用户");
    return;
  }

  assigningUserRoles.value = true;
  try {
    await rbacApi.assignUserRoles(selectedUserId.value, selectedUserRoleIds.value);
    MessagePlugin.success("用户角色已更新");
    await loadUsers();
  } finally {
    assigningUserRoles.value = false;
  }
}

async function batchAppendRoles() {
  if (!canAssignUserRole.value) {
    return;
  }
  if (batchUserIds.value.length === 0) {
    MessagePlugin.warning("请至少选择一个用户");
    return;
  }
  if (batchRoleIds.value.length === 0) {
    MessagePlugin.warning("请至少选择一个角色");
    return;
  }

  batchAppendingRoles.value = true;
  try {
    await rbacApi.batchAppendUserRoles(batchUserIds.value, batchRoleIds.value);
    MessagePlugin.success("已批量追加用户角色");
    batchUserIds.value = [];
    batchRoleIds.value = [];
    await loadUsers();
  } finally {
    batchAppendingRoles.value = false;
  }
}
</script>
