/**
 * 前端权限码常量
 */
export const PermissionConstant = {
  TASK_BUFF_LIST: "task:buff:list",
  TASK_RECORD_LIST: "task:record:list",
  TASK_INVENTORY_VIEW: "task:inventory:view",
  TASK_C5_LIST: "task:c5:list",

  LOG_LIST: "system:logs:list",
  ACCOUNT_LIST: "system:account:list",
  GOODS_LIST: "system:goods:list",
  STICKER_LIST: "system:sticker:list",
  STICKER_SYNC: "system:sticker:sync",

  BUFF_ACCOUNT_SAVE: "buff:account:save",
  BUFF_ACCOUNT_DELETE: "buff:account:delete",
  BUFF_ACCOUNT_CHECK: "buff:account:check",
  BUFF_ACCOUNT_CHECK_ALL: "buff:account:check:all",

  ORDER_RECORD_UPDATE: "order:record:update",
  ORDER_RECORD_DELETE: "order:record:delete",

  SETTINGS_SAVE: "system:settings:save",
  SETTINGS_TEST_NOTIFY: "system:settings:test-notify",

  NOTIFY_SEND: "system:notify:send",
} as const;
