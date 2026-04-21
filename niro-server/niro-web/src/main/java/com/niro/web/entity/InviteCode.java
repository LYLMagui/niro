package com.niro.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统邀请码
 */
@Data
@TableName("sys_invite_code")
public class InviteCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private Long issuerUserId;

    private Integer maxUseCount;

    private Integer usedCount;

    private Integer status;

    private Long usedUserId;

    private LocalDateTime usedAt;

    private LocalDateTime expireTime;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
