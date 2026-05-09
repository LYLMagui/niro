package com.niro.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.niro.web.config.PostgresJsonTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName(value = "c5_sniping_hit_record_v2", autoResultMap = true)
public class C5SnipingHitRecordV2 {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long accountId;
    private String listingId;
    private BigDecimal listingPrice;
    private BigDecimal paintwear;
    private String decisionResult;
    @TableField(typeHandler = PostgresJsonTypeHandler.class, jdbcType = JdbcType.OTHER)
    private Map<String, Object> itemSnapshotJson;
    private LocalDateTime hitAt;
    private LocalDateTime createTime;
}
