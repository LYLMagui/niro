package com.niro.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * BUFF 历史扫货任务实体。
 */
@Data
@TableName("buff_scan_task")
public class BuffScanTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 任务名称。
     */
    private String name;

    /**
     * 创建用户 ID。
     */
    private Long userId;
}
