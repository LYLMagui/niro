package com.niro.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.niro.web.entity.RoleResourcePublished;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色资源已发布 Mapper
 */
@Mapper
public interface RoleResourcePublishedMapper extends BaseMapper<RoleResourcePublished> {

    @Delete("delete from role_resource_published")
    int deleteAll();
}
