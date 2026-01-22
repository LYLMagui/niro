package com.niro.sdk.c5.request.account;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class C5SteamInfoRequest {
    /**
     * 上一页列表中最后一条记录的relationId，初始可以传0
     */
    private String minRelationId;
    /**
     * 每页展示数量
     */
    private Integer limit;
}
