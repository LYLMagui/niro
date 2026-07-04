package com.niro.web.manager;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.entity.RoleResourceDraft;
import com.niro.web.mapper.RoleResourceDraftMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 角色资源草稿管理器
 */
@Service
public class RoleResourceDraftMapperManager extends ServiceImpl<RoleResourceDraftMapper, RoleResourceDraft> {

    public List<RoleResourceDraft> listByRoleId(Long roleId) {
        return this.lambdaQuery()
                .eq(RoleResourceDraft::getRoleId, roleId)
                .eq(RoleResourceDraft::getDelFlag, 0)
                .list();
    }

    public boolean existsByRoleId(Long roleId) {
        return this.lambdaQuery()
                .eq(RoleResourceDraft::getRoleId, roleId)
                .eq(RoleResourceDraft::getDelFlag, 0)
                .exists();
    }

    public List<RoleResourceDraft> listByResourceId(Long resourceId) {
        return this.lambdaQuery()
                .eq(RoleResourceDraft::getResourceId, resourceId)
                .eq(RoleResourceDraft::getDelFlag, 0)
                .list();
    }

    public int deleteByRoleId(Long roleId) {
        return baseMapper.deleteByRoleId(roleId);
    }
}
