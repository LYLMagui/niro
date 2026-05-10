package com.niro.web.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 开箱记录 C5 在售查询参数
 */
@Data
@Schema(description = "开箱记录 C5 在售查询参数")
public class UnboxRecordC5ListingQueryParam {

    @NotNull(message = "CS2商品ID不能为空")
    @Schema(description = "CS2商品ID，对应 cs2_goods.id")
    private Long cs2GoodsId;

    @DecimalMin(value = "0", message = "磨损区间最小值不能小于0")
    @DecimalMax(value = "1", message = "磨损区间最小值不能大于1")
    @Schema(description = "磨损区间最小值，取值范围0到1")
    private BigDecimal wearMin;

    @DecimalMin(value = "0", message = "磨损区间最大值不能小于0")
    @DecimalMax(value = "1", message = "磨损区间最大值不能大于1")
    @Schema(description = "磨损区间最大值，取值范围0到1")
    private BigDecimal wearMax;

    @Min(value = 0, message = "外观不能小于0")
    @Max(value = 4, message = "外观不能大于4")
    @Schema(description = "外观，0=Factory New，1=Minimal Wear，2=Field-Tested，3=Well-Worn，4=Battle-Scarred")
    private Integer exterior;

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码必须大于0")
    @Schema(description = "页码，从1开始")
    private Integer pageNum;

    @NotNull(message = "每页数量不能为空")
    @Min(value = 1, message = "每页数量必须大于0")
    @Schema(description = "每页数量")
    private Integer pageSize;

    /**
     * 是否强制刷新本地 C5 价格快照。
     */
    private Boolean refresh;
}
