package com.niro.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 开箱记录 OCR 识别结果
 */
@Data
@Schema(description = "开箱记录 OCR 识别结果")
public class UnboxRecordOcrResultDTO {

    @Schema(description = "商品名称")
    private String name;

    @Schema(description = "游戏内价格")
    private BigDecimal price;

    @Schema(description = "磨损")
    private BigDecimal wear;

    @Schema(description = "外观：0崭新出厂、1略有磨损、2久经沙场、3战痕累累、4破损不堪")
    private Integer exterior;
}
