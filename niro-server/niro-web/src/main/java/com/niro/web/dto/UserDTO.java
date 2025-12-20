package com.niro.web.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 *
 *
 * @author liyl
 * @date 2025/12/18
 */
@Data
@Schema(description = "用户传输对象")
public class UserDTO {
    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long id;

    /**
     * 用户名
     */
    @Schema(description = "账号")
    private String username;
    

    /**
     * 昵称
     */
    @Schema(description = "昵称")
    private String nickname;

    /**
     * 邮箱
     */
    @Schema(description = "邮箱")
    private String email;

    /**
     * 头像URL
     */
    @Schema(description = "头像URL")
    private String avatar;

    /**
     * 状态: 1-正常, 0-禁用
     */
    @Schema(description = "状态: 1-正常, 0-禁用")
    private Integer status;

    /**
     * 是否删除: 0-否, 1-是
     */
    @TableLogic
    @Schema(description = "是否删除: 0-否, 1-是")
    private Integer isDelete;
}
