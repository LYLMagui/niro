package com.niro.web.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 邀请码详情DTO
 */
@Data
public class InviteCodeDetailDTO {

    private Long id;

    private String code;

    private Integer status;

    private String availability;

    private Long issuerUserId;

    private String creatorName;

    private Long usedUserId;

    private Boolean forever;

    private String registrationNickname;

    private String registrationEmail;

    private String registrationAccountStatus;

    private LocalDateTime usedAt;

    private LocalDateTime expireTime;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
