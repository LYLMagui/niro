package com.niro.web.dto.report;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BuffGoodsReportDTO {
    @JsonProperty("goods_id")
    private Long goodsId;

    private String name;

    @JsonProperty("market_hash_name")
    private String marketHashName;

    @JsonProperty("short_name")
    private String shortName;

    @JsonProperty("icon_url")
    private String iconUrl;

    @JsonProperty("original_icon_url")
    private String originalIconUrl;

    @JsonProperty("category_id")
    private Long categoryId;

    @JsonProperty("internal_name")
    private String internalName;

    private String rarity;
    private String exterior;
    
    // 直接映射为 Map，MyBatis-Plus 的 JacksonTypeHandler 会自动处理
    private Map<String, Object> tags;
}
