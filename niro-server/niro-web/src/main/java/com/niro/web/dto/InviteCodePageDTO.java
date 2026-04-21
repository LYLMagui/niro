package com.niro.web.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 邀请码分页DTO
 */
@Data
public class InviteCodePageDTO {

    private Long id;

    private String code;

    private Integer status;

    private String availability;

    private Long usedUserId;

    private String registrationNickname;

    private String registrationEmail;

    private String registrationAccountStatus;

    private LocalDateTime usedAt;

    private String creatorName;

    private Boolean forever;

    private LocalDateTime expireTime;

    private String remark;

    private LocalDateTime createdAt;
}
