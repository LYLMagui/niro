package com.niro.web.constant;

import java.time.LocalDateTime;

/**
 * 邀请码相关常量。
 */
public final class InviteCodeConstants {

    private InviteCodeConstants() {
    }

    /**
     * 随机邀请码可用字符集。
     */
    public static final String INVITE_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    /**
     * 邀请码固定长度。
     */
    public static final int INVITE_CODE_LENGTH = 10;

    /**
     * 邀请码启用状态。
     */
    public static final Integer STATUS_ENABLED = 1;

    /**
     * 未被使用的邀请码用户 ID 占位值。
     */
    public static final Long UNUSED_USER_ID = 0L;

    /**
     * 历史已使用邀请码的兼容用户 ID 占位值。
     */
    public static final Long HISTORICAL_USED_USER_ID = -1L;

    /**
     * 未使用时间占位值。
     */
    public static final LocalDateTime UNUSED_AT = LocalDateTime.of(1970, 1, 1, 0, 0, 0);

    /**
     * 系统创建邀请码时使用的用户 ID。
     */
    public static final Long SYSTEM_USER_ID = 0L;

    /**
     * 邀请码可用状态标识。
     */
    public static final String AVAILABILITY_AVAILABLE = "available";

    /**
     * 邀请码已使用状态标识。
     */
    public static final String AVAILABILITY_USED = "used";

    /**
     * 邀请码已过期状态标识。
     */
    public static final String AVAILABILITY_EXPIRED = "expired";

    /**
     * 邀请码已停用状态标识。
     */
    public static final String AVAILABILITY_DISABLED = "disabled";

    /**
     * 永久有效邀请码的过期时间占位值。
     */
    public static final LocalDateTime FOREVER_EXPIRE_TIME = LocalDateTime.of(9999, 12, 31, 23, 59, 59);
}
