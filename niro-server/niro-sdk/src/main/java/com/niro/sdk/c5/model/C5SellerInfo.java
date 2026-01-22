package com.niro.sdk.c5.model;

import lombok.Data;

@Data
public class C5SellerInfo {
    private String userId;
    private String thirdUserId;
    private String nickname;
    private String avatar;
    private Integer lastActive;
    private Integer platformId;
    private String steamId;
    private DeliveryInfo deliveryInfo;

    @Data
    public static class DeliveryInfo {
        private DeliveryStat day7;
        private DeliveryStat day15;
    }

    @Data
    public static class DeliveryStat {
        private String deliveryAvgTime;
        private Integer deliveryNoneNum;
        private String deliverySuccessRate;
    }
}
