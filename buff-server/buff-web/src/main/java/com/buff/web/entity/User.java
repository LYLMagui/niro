package com.buff.web.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 *
 *
 * @author liyl
 * @date 2025/12/18
 */
@Data
@TableName(value = "users")
public class User {
    private Long id;
    private String name;
}
