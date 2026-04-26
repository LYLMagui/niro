package com.niro.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.niro.web.entity.ResourcePublished;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

/**
 * 资源已发布 Mapper
 */
@Mapper
public interface ResourcePublishedMapper extends BaseMapper<ResourcePublished> {

    @Delete("delete from resource_published")
    int deleteAll();
}
