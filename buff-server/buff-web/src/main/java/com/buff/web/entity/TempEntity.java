package com.buff.web.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;

/**
 * @author liyl
 * @date 2025-12-18
 * @description 临时实体类
 */
@Data
@TableName("temp_table")
public class TempEntity implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * ID
     */
    private Long id;
}
