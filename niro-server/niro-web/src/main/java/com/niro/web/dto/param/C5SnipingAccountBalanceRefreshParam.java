package com.niro.web.dto.param;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * C5 扫货独立账号余额刷新参数。
 */
@Data
public class C5SnipingAccountBalanceRefreshParam {

    /**
     * 待刷新余额的账号 ID 列表。
     */
    @NotEmpty(message = "账号ID列表不能为空")
    private List<Long> accountIds;
}
