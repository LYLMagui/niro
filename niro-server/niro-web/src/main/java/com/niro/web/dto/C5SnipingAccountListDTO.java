package com.niro.web.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * C5 扫货账号列表 DTO。
 */
@Data
public class C5SnipingAccountListDTO {

    /**
     * 账号列表。
     */
    private List<C5SnipingAccountDTO> records;

    /**
     * 当前用户全部 C5 资金账号余额合计，相同 AppKey 只统计一次。
     */
    private BigDecimal totalBalance;
}
