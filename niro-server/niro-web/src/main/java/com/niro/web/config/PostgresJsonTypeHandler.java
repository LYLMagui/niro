package com.niro.web.config;

import com.baomidou.mybatisplus.extension.handlers.AbstractJsonTypeHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * PostgreSQL JSON 字段类型处理器
 * 解决 "column is of type json but expression is of type character varying" 问题
 */
@Slf4j
@MappedTypes({Object.class})
@MappedJdbcTypes(JdbcType.OTHER)
public class PostgresJsonTypeHandler extends AbstractJsonTypeHandler<Object> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public PostgresJsonTypeHandler(Class<Object> type) {
        super(type);
    }

    @Override
    public Object parse(String json) {
        try {
            if (json == null || json.isEmpty()) {
                return null;
            }
            return OBJECT_MAPPER.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            log.error("JSON 解析失败: {}", json, e);
            return null;
        }
    }

    @Override
    public String toJson(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON 序列化失败", e);
            return "{}";
        }
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType) throws SQLException {
        PGobject jsonObject = new PGobject();
        jsonObject.setType("json");
        jsonObject.setValue(toJson(parameter));
        ps.setObject(i, jsonObject);
    }
}
