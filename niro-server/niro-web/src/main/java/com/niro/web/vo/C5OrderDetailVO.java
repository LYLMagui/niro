package com.niro.web.vo;

import com.niro.sdk.c5.response.trade.C5OrderDetailResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * C5 订单详情 VO
 *
 * @author niro
 * @since 2026-02-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class C5OrderDetailVO extends C5OrderDetailResponse {
    /**
     * 创建时间 (格式化后)
     */
    private String createTimeStr;
}
