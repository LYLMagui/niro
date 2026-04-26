package com.niro.web.manager;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.entity.ResourcePublished;
import com.niro.web.mapper.ResourcePublishedMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 资源已发布管理器
 */
@Service
public class ResourcePublishedMapperManager extends ServiceImpl<ResourcePublishedMapper, ResourcePublished> {

    public List<ResourcePublished> listActiveResources() {
        return this.lambdaQuery()
                .eq(ResourcePublished::getDelFlag, 0)
                .orderByAsc(ResourcePublished::getSortOrder, ResourcePublished::getId)
                .list();
    }

    public ResourcePublished findByResourceKey(String resourceKey) {
        return this.lambdaQuery()
                .eq(ResourcePublished::getResourceKey, resourceKey)
                .eq(ResourcePublished::getDelFlag, 0)
                .one();
    }

    public int deleteAll() {
        return baseMapper.deleteAll();
    }
}
