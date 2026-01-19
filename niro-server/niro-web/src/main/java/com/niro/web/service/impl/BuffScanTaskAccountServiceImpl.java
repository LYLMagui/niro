package com.niro.web.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.entity.BuffScanTaskAccount;
import com.niro.web.mapper.BuffScanTaskAccountMapper;
import com.niro.web.service.BuffScanTaskAccountService;
import org.springframework.stereotype.Service;

/**
 * 任务与账号关联服务实现类
 */
@Service
public class BuffScanTaskAccountServiceImpl extends ServiceImpl<BuffScanTaskAccountMapper, BuffScanTaskAccount> implements BuffScanTaskAccountService {
}
