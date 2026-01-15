package com.niro.web.dto;

import com.niro.web.enums.BuffAccountRoleEnum;
import com.niro.web.enums.BuffAccountStatusEnum;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * BUFF 账号配置 DTO
 *
 * @author niro
 * @since 2026-01-15
 */
@Data
public class BuffAccountDTO {

    private Long id;

    private Long userId;

    private String accountName;

    private String buffCookie;

    private BuffAccountRoleEnum role;

    private BuffAccountStatusEnum status;

    private Integer weight;

    private BigDecimal balance;

    private Integer failCount;

    private LocalDateTime lastCheckTime;

    private String userAgent;

    private String remark;

    private String warningMsg;

    private Integer todayScanCount;

    private Integer tradeSuccessCount;

    private Integer tradeTotalCount;

    /**
     * 下单成功率 (成功数 / 总数)
     */
    public Double getTradeSuccessRate() {
        if (tradeTotalCount == null || tradeTotalCount == 0) {
            return 0.0;
        }
        return (double) (tradeSuccessCount != null ? tradeSuccessCount : 0) / tradeTotalCount;
    }

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
