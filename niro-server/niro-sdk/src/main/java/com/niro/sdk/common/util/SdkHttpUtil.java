package com.niro.sdk.common.util;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.net.url.UrlBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * SDK HTTP 通用工具。
 */
public final class SdkHttpUtil {

    /**
     * 根据完整 URL 和查询参数构建 URI，自动忽略 null 值参数。
     */
    public static URI buildUri(String fullUrl, Map<String, Object> queryParams) {
        UrlBuilder urlBuilder = UrlBuilder.ofHttp(fullUrl);
        queryParams.forEach((key, value) -> {
            if (value != null) {
                urlBuilder.addQuery(key, value);
            }
        });
        return urlBuilder.toURI();
    }

    /**
     * 规范化接口路径，确保 endpoint 以斜杠开头。
     */
    public static String normalizeEndpoint(String endpoint) {
        return endpoint.startsWith("/") ? endpoint : "/" + endpoint;
    }

    /**
     * 去除基础地址末尾多余的斜杠。
     */
    public static String trimTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    /**
     * 将 null、Map 或普通请求对象统一转换为字符串 key 的参数 Map。
     */
    public static Map<String, Object> toParamMap(Object params) {
        if (Objects.isNull(params)) {
            return Collections.emptyMap();
        }
        if (params instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                if (k != null) {
                    result.put(String.valueOf(k), v);
                }
            });
            return result;
        }
        return BeanUtil.beanToMap(params, false, true);
    }

    /**
     * 汇总 query 和 body 参数 key，用于安全日志输出。
     */
    public static String summarizeParams(Map<String, Object> queryParams, Map<String, Object> bodyParams,
                                         Set<String> sensitiveKeys) {
        List<String> parts = new ArrayList<>(2);
        if (!queryParams.isEmpty()) {
            parts.add("query=" + safeKeys(queryParams, sensitiveKeys));
        }
        if (!bodyParams.isEmpty()) {
            parts.add("body=" + safeKeys(bodyParams, sensitiveKeys));
        }
        return String.join(", ", parts);
    }

    /**
     * 输出参数 key 列表，并对敏感 key 使用占位符脱敏。
     */
    public static String safeKeys(Map<String, Object> map, Set<String> sensitiveKeys) {
        return map.keySet().stream()
                .map(k -> sensitiveKeys.contains(k.toLowerCase(Locale.ROOT)) ? k + "=***" : k)
                .collect(Collectors.joining(",", "[", "]"));
    }

    /**
     * 按指定长度截断字符串，避免日志输出过长内容。
     */
    public static String truncate(String s, int limit) {
        if (s == null) {
            return "";
        }
        return s.length() > limit
                ? s.substring(0, limit) + "...(truncated, total=" + s.length() + ")"
                : s;
    }

    /**
     * 生成指定长度的短 traceId。
     */
    public static String newTraceId(int length) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, length);
    }

    /**
     * 根据开始纳秒时间计算已耗费毫秒数。
     */
    public static long elapsedMs(long startNs, long nanosPerMilli) {
        return (System.nanoTime() - startNs) / nanosPerMilli;
    }

    private SdkHttpUtil() {
    }
}
