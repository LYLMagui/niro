package com.niro.web.manager;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.entity.BuffAccount;
import com.niro.web.mapper.BuffAccountMapper;
import org.springframework.stereotype.Service;

/**
 * BUFF账号基础数据管理器
 * 负责账号的CRUD操作，解耦循环依赖
 *
 * @author niro
 * @date 2026/02/04
 */
@Service
public class BuffAccountMapperManager extends ServiceImpl<BuffAccountMapper, BuffAccount> {

}
