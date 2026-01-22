package com.niro.sdk.c5.response.account;

import com.niro.sdk.c5.model.C5SellerInfo;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class C5SteamInfoResponse {
    private String uid;
    private String avatar;
    private String nickname;
    private BigDecimal balance;
    private List<C5SellerInfo> steamList;
}
