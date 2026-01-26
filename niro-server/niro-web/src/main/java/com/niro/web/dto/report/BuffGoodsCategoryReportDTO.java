package com.niro.web.dto.report;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BuffGoodsCategoryReportDTO {
    private String name;

    @JsonProperty("internal_name")
    private String internalName;

    @JsonProperty("category_type")
    private String categoryType;

    @JsonProperty("full_internal_name")
    private String fullInternalName;

    @JsonProperty("parent_id")
    private Long parentId;
}
