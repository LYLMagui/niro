package com.niro.web.service.impl;

import org.springframework.stereotype.Service;

import com.niro.web.manager.BuffScanTaskAccountManagerMapper;
import com.niro.web.service.BuffScanTaskAccountService;

import lombok.RequiredArgsConstructor;

/**
 * 任务与账号关联服务实现类
 */
@Service
@RequiredArgsConstructor
public class BuffScanTaskAccountServiceImpl implements BuffScanTaskAccountService {

    private final BuffScanTaskAccountManagerMapper buffScanTaskAccountManagerMapper;
}
