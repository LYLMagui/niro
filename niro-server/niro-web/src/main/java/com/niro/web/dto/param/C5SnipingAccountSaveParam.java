package com.niro.web.dto.param;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * C5 扫货独立账号保存参数。
 */
@Data
public class C5SnipingAccountSaveParam {

    /**
     * 账号 ID；为空时创建，非空时更新。
     */
    private Long id;

    /**
     * 账号展示名称。
     */
    @NotBlank(message = "账号名称不能为空")
    private String accountName;

    /**
     * C5 AppKey。
     */
    @NotBlank(message = "C5 AppKey不能为空")
    private String c5AppKey;

    /**
     * Steam 交易链接。
     */
    @NotBlank(message = "Steam交易链接不能为空")
    private String steamTradeUrl;

    /**
     * Steam ID，用于 C5 库存接口。
     */
    private String steamId;

    /**
     * 用户备注。
     */
    private String remark;

    /**
     * 账号级并发上限。
     */
    @Min(value = 1, message = "并发上限必须大于等于1")
    private Integer concurrencyLimit;

    /**
     * 账号级最大在途下单尝试数。
     */
    @Min(value = 1, message = "最大在途下单数必须大于等于1")
    private Integer maxInFlightAttempts;
}
