package com.niro.web.constant;

/**
 * 最小权限码定义
 */
public final class PermissionConstants {

    private PermissionConstants() {
    }

    public static final String TASK_SCAN_LIST = "task:scan:list";
    public static final String TASK_BUFF_LIST = "task:buff:list";
    public static final String TASK_RECORD_LIST = "task:record:list";
    public static final String TASK_INVENTORY_VIEW = "task:inventory:view";
    public static final String TASK_C5_LIST = "task:c5:list";

    public static final String LOG_LIST = "system:logs:list";
    public static final String ACCOUNT_LIST = "system:account:list";
    public static final String GOODS_LIST = "system:goods:list";
    public static final String STICKER_LIST = "system:sticker:list";
    public static final String STICKER_SYNC = "system:sticker:sync";

    public static final String BUFF_ACCOUNT_SAVE = "buff:account:save";
    public static final String BUFF_ACCOUNT_DELETE = "buff:account:delete";
    public static final String BUFF_ACCOUNT_CHECK = "buff:account:check";
    public static final String BUFF_ACCOUNT_CHECK_ALL = "buff:account:check:all";

    public static final String ORDER_RECORD_UPDATE = "order:record:update";
    public static final String ORDER_RECORD_DELETE = "order:record:delete";

    public static final String SETTINGS_SAVE = "system:settings:save";
    public static final String SETTINGS_TEST_NOTIFY = "system:settings:test-notify";
    public static final String INVITE_CODE_MANAGE = "system:invite-code:manage";

    public static final String NOTIFY_SEND = "system:notify:send";
}
