package com.niro.web.manager;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.entity.RoleResourcePublished;
import com.niro.web.mapper.RoleResourcePublishedMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 角色资源已发布管理器
 */
@Service
public class RoleResourcePublishedMapperManager extends ServiceImpl<RoleResourcePublishedMapper, RoleResourcePublished> {

    public List<RoleResourcePublished> listByRoleId(Long roleId) {
        return this.lambdaQuery()
                .eq(RoleResourcePublished::getRoleId, roleId)
                .eq(RoleResourcePublished::getDelFlag, 0)
                .list();
    }

    public List<RoleResourcePublished> listByResourceId(Long resourceId) {
        return this.lambdaQuery()
                .eq(RoleResourcePublished::getResourceId, resourceId)
                .eq(RoleResourcePublished::getDelFlag, 0)
                .list();
    }

    public int deleteAll() {
        return baseMapper.deleteAll();
    }
}
