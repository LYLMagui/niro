package com.niro.sdk.c5.response.market;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class C5BatchPriceInfo {
    private String itemId;
    private String marketHashName;
    private BigDecimal price;
    private Integer count;
    private String website;
}
