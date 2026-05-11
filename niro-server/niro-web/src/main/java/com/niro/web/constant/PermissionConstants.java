package com.niro.web.constant;

/**
 * 最小权限码定义。
 */
public final class PermissionConstants {

    private PermissionConstants() {
    }

    /**
     * 任务与记录查询权限。
     */
    public static final class Task {

        private Task() {
        }

        /**
         * 扫货任务列表权限。
         */
        public static final String SCAN_LIST = "task:scan:list";
        /**
         * 订单记录列表权限。
         */
        public static final String RECORD_LIST = "task:record:list";
        /**
         * 库存视图权限。
         */
        public static final String INVENTORY_VIEW = "task:inventory:view";
        /**
         * C5 扫货任务列表权限。
         */
        public static final String C5_LIST = "task:c5:list";
    }

    /**
     * C5 库存权限。
     */
    public static final class C5Inventory {

        private C5Inventory() {
        }

        /**
         * C5 库存列表权限。
         */
        public static final String LIST = "c5:inventory:list";
        /**
         * C5 库存刷新权限。
         */
        public static final String REFRESH = "c5:inventory:refresh";
    }

    /**
     * 系统基础资源权限。
     */
    public static final class SystemResource {

        private SystemResource() {
        }

        /**
         * 系统日志列表权限。
         */
        public static final String LOG_LIST = "system:logs:list";
        /**
         * 系统账号列表权限。
         */
        public static final String ACCOUNT_LIST = "system:account:list";
        /**
         * 商品列表权限。
         */
        public static final String GOODS_LIST = "system:goods:list";
        /**
         * 数据导出页面查看权限。
         */
        public static final String DATA_EXPORT_VIEW = "statistics:data-export:view";
    }

    /**
     * 订单记录权限。
     */
    public static final class OrderRecord {

        private OrderRecord() {
        }

        /**
         * 更新订单记录权限。
         */
        public static final String UPDATE = "order:record:update";
        /**
         * 删除订单记录权限。
         */
        public static final String DELETE = "order:record:delete";
        /**
         * 同步 C5 订单权限。
         */
        public static final String C5_SYNC = "order:c5:sync";
    }

    /**
     * 系统设置权限。
     */
    public static final class Settings {

        private Settings() {
        }

        /**
         * 保存系统设置权限。
         */
        public static final String SAVE = "system:settings:save";
    }

    /**
     * 邀请码权限。
     */
    public static final class InviteCode {

        private InviteCode() {
        }

        /**
         * 管理邀请码权限。
         */
        public static final String MANAGE = "system:invite-code:manage";
        /**
         * 创建邀请码权限。
         */
        public static final String CREATE = "system:invite-code:create";
        /**
         * 批量创建邀请码权限。
         */
        public static final String BATCH_CREATE = "system:invite-code:batch-create";
        /**
         * 更新邀请码权限。
         */
        public static final String UPDATE = "system:invite-code:update";
        /**
         * 启用邀请码权限。
         */
        public static final String ENABLE = "system:invite-code:enable";
        /**
         * 禁用邀请码权限。
         */
        public static final String DISABLE = "system:invite-code:disable";
    }

    /**
     * 商品同步权限。
     */
    public static final class Goods {

        private Goods() {
        }

        /**
         * 同步商品权限。
         */
        public static final String SYNC = "system:goods:sync";
    }

    /**
     * C5 扫货任务权限。
     */
    public static final class C5SnipingTask {

        private C5SnipingTask() {
        }

        /**
         * 创建 C5 扫货任务权限。
         */
        public static final String CREATE = "c5:sniping-task:create";
        /**
         * 更新 C5 扫货任务权限。
         */
        public static final String UPDATE = "c5:sniping-task:update";
        /**
         * 启用 C5 扫货任务权限。
         */
        public static final String ENABLE = "c5:sniping-task:enable";
        /**
         * 禁用 C5 扫货任务权限。
         */
        public static final String DISABLE = "c5:sniping-task:disable";
        /**
         * 删除 C5 扫货任务权限。
         */
        public static final String DELETE = "c5:sniping-task:delete";
        /**
         * 查看 C5 扫货任务详情权限。
         */
        public static final String DETAIL = "c5:sniping-task:detail";
    }

    /**
     * C5 扫货账号权限。
     */
    public static final class C5SnipingAccount {

        private C5SnipingAccount() {
        }

        /**
         * 创建 C5 扫货账号权限。
         */
        public static final String CREATE = "c5:sniping-account:create";
        /**
         * 更新 C5 扫货账号权限。
         */
        public static final String UPDATE = "c5:sniping-account:update";
        /**
         * 删除 C5 扫货账号权限。
         */
        public static final String DELETE = "c5:sniping-account:delete";
        /**
         * 查看 C5 扫货账号详情权限。
         */
        public static final String DETAIL = "c5:sniping-account:detail";
    }

    /**
     * 新权限系统管理权限。
     */
    public static final class Permission {

        private Permission() {
        }

        /**
         * 读取权限资源权限。
         */
        public static final String RESOURCE_READ = "system:permission:resource:read";
        /**
         * 读取角色授权权限。
         */
        public static final String ROLE_AUTH_READ = "system:permission:role-auth:read";
        /**
         * 管理权限系统权限。
         */
        public static final String MANAGE = "system:permission:manage";
        /**
         * 保存权限资源权限。
         */
        public static final String RESOURCE_SAVE = "system:permission:resource:save";
        /**
         * 创建角色权限。
         */
        public static final String ROLE_CREATE = "system:permission:role:create";
        /**
         * 更新角色权限。
         */
        public static final String ROLE_UPDATE = "system:permission:role:update";
        /**
         * 删除角色权限。
         */
        public static final String ROLE_DELETE = "system:permission:role:delete";
        /**
         * 复制角色权限。
         */
        public static final String ROLE_COPY = "system:permission:role:copy";
        /**
         * 保存角色授权权限。
         */
        public static final String ROLE_AUTH_SAVE = "system:permission:role-auth:save";
        /**
         * 分配用户角色权限。
         */
        public static final String USER_ASSIGN = "system:permission:user:assign";
        /**
         * 预览角色权限。
         */
        public static final String ROLE_PREVIEW = "system:permission:role:preview";
        /**
         * 校验权限发布权限。
         */
        public static final String PUBLISH_VALIDATE = "system:permission:publish:validate";
        /**
         * 发布权限配置权限。
         */
        public static final String PUBLISH = "system:permission:publish";
    }

    /**
     * 开箱记录权限。
     */
    public static final class UnboxRecord {

        private UnboxRecord() {
        }

        /**
         * 创建开箱记录权限。
         */
        public static final String CREATE = "unbox:record:create";
        /**
         * 创建开箱记录导出任务权限。
         */
        public static final String EXPORT = "unbox:record:export";
        /**
         * 更新开箱记录权限。
         */
        public static final String UPDATE = "unbox:record:update";
        /**
         * 删除开箱记录权限。
         */
        public static final String DELETE = "unbox:record:delete";
        /**
         * 识别开箱记录 OCR 权限。
         */
        public static final String OCR = "unbox:record:ocr";
        /**
         * 查询开箱记录 C5 挂单权限。
         */
        public static final String QUERY_C5 = "unbox:record:query-c5";
        /**
         * 添加开箱明细权限。
         */
        public static final String DETAIL_ADD = "unbox:record:detail:add";
        /**
         * 删除开箱明细权限。
         */
        public static final String DETAIL_DELETE = "unbox:record:detail:delete";
        /**
         * 应用开箱记录价格权限。
         */
        public static final String APPLY_PRICE = "unbox:record:apply-price";
        /**
         * 应用开箱记录默认值权限。
         */
        public static final String APPLY_DEFAULTS = "unbox:record:apply-defaults";
    }
}
