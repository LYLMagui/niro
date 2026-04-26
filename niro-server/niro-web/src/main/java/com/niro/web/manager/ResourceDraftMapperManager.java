package com.niro.web.manager;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.entity.ResourceDraft;
import com.niro.web.mapper.ResourceDraftMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 新权限资源草稿管理器
 */
@Service
public class ResourceDraftMapperManager extends ServiceImpl<ResourceDraftMapper, ResourceDraft> {

    public List<ResourceDraft> listActiveResources() {
        return this.lambdaQuery()
                .eq(ResourceDraft::getDelFlag, 0)
                .orderByAsc(ResourceDraft::getSortOrder, ResourceDraft::getId)
                .list();
    }

    public ResourceDraft findByResourceKey(String resourceKey) {
        return this.lambdaQuery()
                .eq(ResourceDraft::getResourceKey, resourceKey)
                .eq(ResourceDraft::getDelFlag, 0)
                .one();
    }
}
