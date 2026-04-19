package com.niro.web.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 开箱记录 C5 在售查询参数
 */
@Data
@Schema(description = "开箱记录 C5 在售查询参数")
public class UnboxRecordC5ListingQueryParam {

    @NotBlank(message = "饰品名称不能为空")
    @Schema(description = "饰品名称")
    private String weaponName;

    @DecimalMin(value = "0", message = "磨损区间最小值不能小于0")
    @DecimalMax(value = "1", message = "磨损区间最小值不能大于1")
    @Schema(description = "磨损区间最小值，取值范围0到1")
    private BigDecimal wearMin;

    @DecimalMin(value = "0", message = "磨损区间最大值不能小于0")
    @DecimalMax(value = "1", message = "磨损区间最大值不能大于1")
    @Schema(description = "磨损区间最大值，取值范围0到1")
    private BigDecimal wearMax;

    @Schema(description = "外观，可为空")
    private Integer exterior;

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码必须大于0")
    @Schema(description = "页码，从1开始")
    private Integer pageNum;

    @NotNull(message = "每页数量不能为空")
    @Min(value = 1, message = "每页数量必须大于0")
    @Schema(description = "每页数量")
    private Integer pageSize;
}
