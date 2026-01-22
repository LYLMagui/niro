package com.niro.web.dto;

import com.niro.web.enums.BuffAccountRoleEnum;
import com.niro.web.enums.BuffAccountStatusEnum;
import com.niro.web.enums.PlatformEnum;
import io.swagger.v3.oas.annotations.media.Schema;
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

    private PlatformEnum platform = PlatformEnum.BUFF;

    private String accountName;

    private String buffCookie;

    private BuffAccountRoleEnum role;

    private BuffAccountStatusEnum status;

    private Integer weight;

    private BigDecimal balance;

    private BigDecimal pendingBalance;

    private Integer failCount;

    private LocalDateTime lastCheckTime;

    private String userAgent;

    private String proxy;

    private Double frequency;

    private String remark;

    private String warningMsg;

    private Boolean checking;

    private Integer todayScanCount;

    private Integer tradeSuccessCount;

    private Integer tradeTotalCount;

    @Schema(description = "API配置信息(JSON)")
    private String apiConfig;

    @Schema(description = "当前绑定的任务ID (若有)")
    private Long boundTaskId;

    @Schema(description = "当前绑定的任务名称 (若有)")
    private String boundTaskName;

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
