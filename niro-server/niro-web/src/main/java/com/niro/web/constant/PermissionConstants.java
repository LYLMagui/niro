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

    public static final String BUFF_ACCOUNT_SAVE = "buff:account:save";
    public static final String BUFF_ACCOUNT_DELETE = "buff:account:delete";
    public static final String BUFF_ACCOUNT_CHECK = "buff:account:check";
    public static final String BUFF_ACCOUNT_CHECK_ALL = "buff:account:check:all";

    public static final String ORDER_RECORD_UPDATE = "order:record:update";
    public static final String ORDER_RECORD_DELETE = "order:record:delete";

    public static final String SETTINGS_SAVE = "system:settings:save";
    public static final String SETTINGS_TEST_NOTIFY = "system:settings:test-notify";
    public static final String INVITE_CODE_MANAGE = "system:invite-code:manage";
    public static final String INVITE_CODE_CREATE = "system:invite-code:create";
    public static final String INVITE_CODE_BATCH_CREATE = "system:invite-code:batch-create";
    public static final String INVITE_CODE_UPDATE = "system:invite-code:update";
    public static final String INVITE_CODE_ENABLE = "system:invite-code:enable";
    public static final String INVITE_CODE_DISABLE = "system:invite-code:disable";

    public static final String ORDER_C5_SYNC = "order:c5:sync";
    public static final String GOODS_SYNC = "system:goods:sync";

    public static final String C5_SNIPING_TASK_CREATE = "c5:sniping-task:create";
    public static final String C5_SNIPING_TASK_UPDATE = "c5:sniping-task:update";
    public static final String C5_SNIPING_TASK_ENABLE = "c5:sniping-task:enable";
    public static final String C5_SNIPING_TASK_DISABLE = "c5:sniping-task:disable";
    public static final String C5_SNIPING_TASK_DELETE = "c5:sniping-task:delete";
    public static final String C5_SNIPING_TASK_DETAIL = "c5:sniping-task:detail";
    public static final String C5_SNIPING_ACCOUNT_CREATE = "c5:sniping-account:create";
    public static final String C5_SNIPING_ACCOUNT_UPDATE = "c5:sniping-account:update";
    public static final String C5_SNIPING_ACCOUNT_DELETE = "c5:sniping-account:delete";
    public static final String C5_SNIPING_ACCOUNT_DETAIL = "c5:sniping-account:detail";

    public static final String PERMISSION_RESOURCE_READ = "system:permission:resource:read";
    public static final String PERMISSION_ROLE_AUTH_READ = "system:permission:role-auth:read";
    public static final String PERMISSION_MANAGE = "system:permission:manage";
    public static final String PERMISSION_RESOURCE_SAVE = "system:permission:resource:save";
    public static final String PERMISSION_ROLE_CREATE = "system:permission:role:create";
    public static final String PERMISSION_ROLE_UPDATE = "system:permission:role:update";
    public static final String PERMISSION_ROLE_DELETE = "system:permission:role:delete";
    public static final String PERMISSION_ROLE_COPY = "system:permission:role:copy";
    public static final String PERMISSION_ROLE_AUTH_SAVE = "system:permission:role-auth:save";
    public static final String PERMISSION_USER_ASSIGN = "system:permission:user:assign";
    public static final String PERMISSION_ROLE_PREVIEW = "system:permission:role:preview";
    public static final String PERMISSION_PUBLISH_VALIDATE = "system:permission:publish:validate";
    public static final String PERMISSION_PUBLISH = "system:permission:publish";

    public static final String UNBOX_RECORD_CREATE = "unbox:record:create";
    public static final String UNBOX_RECORD_UPDATE = "unbox:record:update";
    public static final String UNBOX_RECORD_DELETE = "unbox:record:delete";
    public static final String UNBOX_RECORD_OCR = "unbox:record:ocr";
    public static final String UNBOX_RECORD_QUERY_C5 = "unbox:record:query-c5";
    public static final String UNBOX_RECORD_DETAIL_ADD = "unbox:record:detail:add";
    public static final String UNBOX_RECORD_DETAIL_DELETE = "unbox:record:detail:delete";
    public static final String UNBOX_RECORD_APPLY_PRICE = "unbox:record:apply-price";
    public static final String UNBOX_RECORD_APPLY_DEFAULTS = "unbox:record:apply-defaults";

    public static final String NOTIFY_SEND = "system:notify:send";
}
