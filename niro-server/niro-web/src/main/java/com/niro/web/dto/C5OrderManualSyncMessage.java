package com.niro.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * C5 手动同步消息 DTO
 *
 * @author niro
 * @since 2026-04-21
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class C5OrderManualSyncMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * C5 扫货独立账号 ID
     */
    private Long accountId;

    /**
     * 查询几天前的订单，0 表示今天，-1 表示全部历史
     */
    private Integer daysBefore;

    /**
     * 消息创建时间戳
     */
    private Long timestamp;

}
