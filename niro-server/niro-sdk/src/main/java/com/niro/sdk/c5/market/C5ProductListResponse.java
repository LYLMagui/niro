package com.niro.sdk.c5.market;

import com.niro.sdk.c5.model.C5AssetInfo;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * C5在售列表查询响应 (V2)
 */
@Data
public class C5ProductListResponse {
    /**
     * 商品列表
     */
    private List<ProductDTO> list;
    /**
     * 当前页码
     */
    private Integer pageNum;
    /**
     * 每页数量
     */
    private Integer pageSize;
    /**
     * 是否还有更多数据
     */
    private Boolean hasMore;

    @Data
    public static class ProductDTO {
        /**
         * 商品Id
         */
        private String productId;
        /**
         * 价格
         */
        private BigDecimal price;
        /**
         * 发货方式：1：人工；2：自动
         */
        private Integer delivery;
        /**
         * 备注
         */
        private String remark;
        /**
         * 是否支持议价：false-否；true-是
         */
        private Boolean acceptBargain;
        /**
         * 图片地址
         */
        private String img;
        /**
         * 卖家Uid
         */
        private String sellerUid;
        /**
         * 资产信息
         */
        private C5AssetInfo assetInfo;
    }
}
