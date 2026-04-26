package com.niro.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.niro.web.entity.RoleResourceDraft;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 角色资源草稿 Mapper
 */
@Mapper
public interface RoleResourceDraftMapper extends BaseMapper<RoleResourceDraft> {

    /**
     * 按角色物理删除草稿授权绑定。
     *
     * @param roleId 角色ID
     * @return 删除行数
     */
    @Delete("delete from role_resource_draft where role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);
}
