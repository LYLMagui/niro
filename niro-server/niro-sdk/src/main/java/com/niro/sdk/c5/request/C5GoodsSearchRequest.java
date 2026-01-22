package com.niro.sdk.c5.request;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
public class C5GoodsSearchRequest {
    private Integer appId;
    private String marketHashName;
    private BigDecimal maxPrice;
    private Integer delivery;
    private Integer styleId;
    private Double minWear;
    private Double maxWear;
    private Integer pageNum;
    private Integer pageSize;
}
