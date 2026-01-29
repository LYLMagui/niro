package com.niro.core.util;

import cn.hutool.core.util.StrUtil;
import com.niro.core.exception.BusinessException;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.Collection;

/**
 * 自定义断言工具类
 *
 * @author liyl
 * @date 2025/03/28
 */
public class Assert {

    /**
     * 断言表达式为 true
     *
     * @param expression 表达式
     * @param message    报错信息
     */
    public static void isTrue(boolean expression, String message) {
        if (!expression) {
            throw new BusinessException(message);
        }
    }

    /**
     * 断言表达式为 false
     *
     * @param expression 表达式
     * @param message    报错信息
     */
    public static void isFalse(boolean expression, String message) {
        if (expression) {
            throw new BusinessException(message);
        }
    }

    /**
     * 断言对象必须为空
     *
     * @param object  对象
     * @param message 报错信息
     */
    public static void isNull(@Nullable Object object, String message) {
        if (object != null) {
            throw new BusinessException(message);
        }
    }

    /**
     * 断言对象必须不为空
     *
     * @param object  对象
     * @param message 报错信息
     */
    public static void notNull(@Nullable Object object, String message) {
        if (object == null) {
            throw new BusinessException(message);
        }
    }

    /**
     * 集合必须不为空
     *
     * @param collection 集合
     * @param message    报错信息
     */
    public static void notEmpty(@Nullable Collection<?> collection, String message) {
        if (CollectionUtils.isEmpty(collection)) {
            throw new BusinessException(message);
        }
    }

    /**
     * 数组必须不为空
     *
     * @param array   数组
     * @param message 报错信息
     */
    public static void notEmpty(@Nullable Object[] array, String message) {
        if (ObjectUtils.isEmpty(array)) {
            throw new BusinessException(message);
        }
    }

    /**
     * 断言字符串不能为空白
     *
     * @param text    字符串
     * @param message 报错信息
     */
    public static void notBlank(@Nullable String text, String message) {
        if (StrUtil.isBlank(text)) {
            throw new BusinessException(message);
        }
    }

    // --- 以下为兼容旧代码的保留方法，标记为弃用或直接重写逻辑 ---

    /**
     * true校验 (如果为true则报错)
     * @deprecated 请使用 isFalse
     */
    @Deprecated
    public static void validateTrue(boolean expression, String message) {
        isFalse(expression, message);
    }

    /**
     * 为空校验 (如果为null则报错)
     * @deprecated 请使用 notNull
     */
    @Deprecated
    public static void validateNull(@Nullable Object object, String message) {
        notNull(object, message);
    }

    /**
     * 不为空校验 (如果不为null则报错)
     * @deprecated 请使用 isNull
     */
    @Deprecated
    public static void validateNotNull(@Nullable Object object, String message) {
        isNull(object, message);
    }
}
