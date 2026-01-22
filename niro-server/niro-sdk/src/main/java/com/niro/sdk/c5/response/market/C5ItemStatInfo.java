package com.niro.sdk.c5.response.market;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class C5ItemStatInfo {
    private String marketHashName;
    private String itemId;
    private BigDecimal sellPrice;
    private Integer sellCount;
    private BigDecimal temporaryRental;
    private BigDecimal permanentRental;
}
