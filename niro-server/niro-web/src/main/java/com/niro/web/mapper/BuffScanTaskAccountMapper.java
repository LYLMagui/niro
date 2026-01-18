package com.niro.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.niro.web.entity.BuffScanTaskAccount;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务与账号关联 Mapper 接口
 *
 * @author niro
 * @since 2026-01-18
 */
@Mapper
public interface BuffScanTaskAccountMapper extends BaseMapper<BuffScanTaskAccount> {
}
