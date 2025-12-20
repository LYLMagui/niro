package com.niro.common.util;

import com.niro.common.exception.BusinessException;
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
     * 对象必须不为空
     *
     * @param array   数组
     * @param message 报错信息
     */
    public static void validateEmpty(@Nullable Object[] array, String message) {
        if (ObjectUtils.isEmpty(array)) {
            throw new BusinessException(message);
        }
    }


    /**
     * 对象必须为空
     *
     * @param object  对象
     * @param message 报错信息
     */
    public static void notNull(@Nullable Object object, String message) {
        if (object != null) {
            throw new BusinessException(message);
        }
    }

    /**
     * 为空校验
     *
     * @param object  对象
     * @param message 报错信息
     */
    public static void validateNull(@Nullable Object object, String message) {
        if (object == null) {
            throw new BusinessException(message);
        }
    }

    /**
     * 不为空校验
     *
     * @param object  对象
     * @param message 报错信息
     */
    public static void validateNotNull(@Nullable Object object, String message) {
        if (object != null) {
            throw new BusinessException(message);
        }
    }

    /**
     * true校验
     *
     * @param expression 表达式
     * @param message    报错信息
     */
    public static void validateTrue(boolean expression, String message) {
        if (expression) {
            throw new BusinessException(message);
        }
    }

    /**
     * 集合为空校验
     *
     * @param collection 集合
     * @param message    报错信息
     */
    public static void validateEmpty(@Nullable Collection<?> collection, String message) {
        if (CollectionUtils.isEmpty(collection)) {
            throw new BusinessException(message);
        }
    }


}
