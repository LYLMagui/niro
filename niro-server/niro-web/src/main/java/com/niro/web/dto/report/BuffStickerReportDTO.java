package com.niro.web.dto.report;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.math.BigDecimal;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BuffStickerReportDTO {
    @JsonProperty("sticker_id")
    private Long stickerId;

    private String name;

    @JsonProperty("image_url")
    private String imageUrl;

    private BigDecimal price;

    @JsonProperty("sell_num")
    private Integer sellNum;
}
