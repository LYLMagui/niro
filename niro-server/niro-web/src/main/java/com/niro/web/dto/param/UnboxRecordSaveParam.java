package com.niro.web.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 开箱记录保存参数
 */
@Data
@Schema(description = "开箱记录保存参数")
public class UnboxRecordSaveParam {

    @NotNull(message = "箱子商品ID不能为空")
    @Schema(description = "箱子商品ID，对应 buff_goods.id")
    private Long goodsId;

    @NotNull(message = "开箱日期不能为空")
    @Schema(description = "开箱日期")
    private LocalDate unboxDate;

    @NotNull(message = "默认折扣不能为空")
    @DecimalMin(value = "0", message = "默认折扣不能小于0")
    @DecimalMax(value = "1", message = "默认折扣不能大于1")
    @Schema(description = "默认折扣")
    private BigDecimal defaultDiscount;

    @Schema(description = "备注")
    private String note;

    @Valid
    @Schema(description = "开箱记录明细列表")
    private List<UnboxRecordItemParam> items = new ArrayList<>();
}
