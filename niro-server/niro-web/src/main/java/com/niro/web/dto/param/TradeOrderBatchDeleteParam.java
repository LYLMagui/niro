package com.niro.web.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 订单批量删除参数
 */
@Data
@Schema(description = "订单批量删除参数")
public class TradeOrderBatchDeleteParam {

    @Schema(description = "订单ID列表")
    private List<Long> ids = new ArrayList<>();
}
