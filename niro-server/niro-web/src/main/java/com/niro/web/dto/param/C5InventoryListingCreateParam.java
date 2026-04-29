package com.niro.web.dto.param;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * C5 库存上架提交参数。
 */
@Data
public class C5InventoryListingCreateParam {

    /**
     * C5 扫货账号 ID。
     */
    @NotNull(message = "账号ID不能为空")
    private Long accountId;

    /**
     * 上架描述。
     */
    private String description;

    /**
     * 是否允许还价：0 否，1 是。
     */
    @NotNull(message = "是否允许还价不能为空")
    private Integer acceptBargain = 0;

    /**
     * 待上架库存明细。
     */
    @Valid
    @NotEmpty(message = "上架库存明细不能为空")
    private List<C5InventoryListingCreateItemParam> items;
}
