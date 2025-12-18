package com.buff.web.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.buff.web.entity.TempEntity;
import com.buff.web.mapper.TempMapper;
import com.buff.web.service.TempService;
import org.springframework.stereotype.Service;

/**
 * @author liyl
 * @date 2025-12-18
 * @description 临时服务实现类
 */
@Service
public class TempServiceImpl extends ServiceImpl<TempMapper, TempEntity> implements TempService {
}
