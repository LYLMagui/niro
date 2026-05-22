package com.niro.core.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.niro.core.constant.LogSanitizeConstant;

import java.util.Iterator;
import java.util.Map;

/**
 * 日志参数脱敏序列化工具
 *
 * @author niro
 * @since 2026-05-20
 */
public final class LogSanitizer {

    private LogSanitizer() {
    }

    /**
     * 将对象脱敏后序列化为字符串。
     *
     * @param mapper Jackson 对象映射器
     * @param value 待序列化对象
     * @param maxLength 最大输出长度
     * @return 脱敏后的字符串
     */
    public static String stringify(ObjectMapper mapper, Object value, int maxLength) {
        try {
            JsonNode root = mapper.valueToTree(value);
            sanitize(root);
            String json = mapper.writeValueAsString(root);
            if (json.length() > maxLength) {
                return json.substring(0, maxLength) + "...";
            }
            return json;
        } catch (Exception e) {
            return "无法序列化参数";
        }
    }

    private static void sanitize(JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey() == null ? "" : entry.getKey().toLowerCase();
                if (LogSanitizeConstant.FULL_MASK_FIELDS.contains(key)) {
                    objectNode.put(entry.getKey(), LogSanitizeConstant.FULL_MASK);
                } else if (LogSanitizeConstant.PARTIAL_MASK_FIELDS.contains(key)) {
                    objectNode.put(entry.getKey(), partialMask(entry.getValue()));
                } else {
                    sanitize(entry.getValue());
                }
            }
        } else if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (JsonNode element : arrayNode) {
                sanitize(element);
            }
        }
    }

    private static String partialMask(JsonNode value) {
        if (value == null || !value.isTextual()) {
            return LogSanitizeConstant.FULL_MASK;
        }
        String text = value.asText();
        if (text.length() <= 7) {
            return LogSanitizeConstant.FULL_MASK;
        }
        return text.substring(0, 3) + LogSanitizeConstant.FULL_MASK + text.substring(text.length() - 4);
    }
}
