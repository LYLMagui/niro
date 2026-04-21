package com.niro.web.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 批量生成邀请码结果DTO
 */
@Data
public class InviteCodeBatchCreateResultDTO {

    private List<InviteCodeCreatedDTO> records;

    @Data
    public static class InviteCodeCreatedDTO {
        private Long id;
        private String code;
        private Boolean forever;
        private LocalDateTime expireTime;
        private String remark;
    }
}
