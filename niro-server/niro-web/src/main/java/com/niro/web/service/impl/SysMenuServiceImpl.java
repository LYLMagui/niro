package com.niro.web.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.constant.UserConstants;
import com.niro.web.dto.MetaDTO;
import com.niro.web.dto.RouterDTO;
import com.niro.web.entity.SysMenu;
import com.niro.web.entity.SysRoleMenu;
import com.niro.web.entity.SysUserRole;
import com.niro.web.enums.MenuTypeEnum;
import com.niro.web.enums.YesNoEnum;
import com.niro.web.mapper.SysMenuMapper;
import com.niro.web.service.SysMenuService;
import com.niro.web.service.SysRoleMenuService;
import com.niro.web.service.SysUserRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 菜单业务层实现
 */
@Service
@RequiredArgsConstructor
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {

    private final SysUserRoleService sysUserRoleService;
    private final SysRoleMenuService sysRoleMenuService;

    @Override
    public List<SysMenu> selectMenuTreeByUserId(Long userId) {
        List<SysMenu> menus;
        if (UserConstants.ADMIN_ID.equals(userId)) {
            // 管理员显示所有菜单
            menus = this.lambdaQuery()
                    .in(SysMenu::getType, MenuTypeEnum.DIR.getCode(), MenuTypeEnum.MENU.getCode())
                    .eq(SysMenu::getStatus, UserConstants.MENU_STATUS_NORMAL)
                    .orderByAsc(SysMenu::getSortOrder)
                    .list();
        } else {
            // 1. 根据用户ID查询角色
            List<SysUserRole> userRoles = sysUserRoleService.lambdaQuery()
                    .eq(SysUserRole::getUserId, userId)
                    .list();
            if (CollUtil.isEmpty(userRoles)) {
                return Collections.emptyList();
            }
            List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());

            // 2. 根据角色查询菜单ID
            List<SysRoleMenu> roleMenus = sysRoleMenuService.lambdaQuery()
                    .in(SysRoleMenu::getRoleId, roleIds)
                    .list();
            if (CollUtil.isEmpty(roleMenus)) {
                return Collections.emptyList();
            }
            Set<Long> menuIds = roleMenus.stream().map(SysRoleMenu::getMenuId).collect(Collectors.toSet());

            // 3. 查询菜单
            menus = this.lambdaQuery()
                    .in(SysMenu::getId, menuIds)
                    .in(SysMenu::getType, MenuTypeEnum.DIR.getCode(), MenuTypeEnum.MENU.getCode())
                    .eq(SysMenu::getStatus, UserConstants.MENU_STATUS_NORMAL)
                    .orderByAsc(SysMenu::getSortOrder)
                    .list();
        }

        return buildMenuTree(menus);
    }

    @Override
    public List<String> selectPermsByUserId(Long userId) {
        List<String> perms = new ArrayList<>();
        if (UserConstants.ADMIN_ID.equals(userId)) {
            perms.add("*:*:*");
            return perms;
        }

        // 1. 根据用户ID查询角色
        List<SysUserRole> userRoles = sysUserRoleService.lambdaQuery()
                .eq(SysUserRole::getUserId, userId)
                .list();
        if (CollUtil.isEmpty(userRoles)) {
            return perms;
        }
        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());

        // 2. 根据角色查询菜单ID
        List<SysRoleMenu> roleMenus = sysRoleMenuService.lambdaQuery()
                .in(SysRoleMenu::getRoleId, roleIds)
                .list();
        if (CollUtil.isEmpty(roleMenus)) {
            return perms;
        }
        Set<Long> menuIds = roleMenus.stream().map(SysRoleMenu::getMenuId).collect(Collectors.toSet());

        // 3. 查询菜单权限
        List<SysMenu> menus = this.lambdaQuery()
                .in(SysMenu::getId, menuIds)
                .eq(SysMenu::getStatus, UserConstants.MENU_STATUS_NORMAL)
                .list();

        for (SysMenu menu : menus) {
            if (StrUtil.isNotEmpty(menu.getPermission())) {
                perms.add(menu.getPermission());
            }
        }
        return perms;
    }

    /**
     * 构建前端路由所需要的菜单
     *
     * @param menus 菜单列表
     * @return 路由列表
     */
    @Override
    public List<RouterDTO> buildMenus(List<SysMenu> menus) {
        return buildMenusRecursive(menus);
    }

    /**
     * 递归构建路由
     */
    private List<RouterDTO> buildMenusRecursive(List<SysMenu> menus) {
        List<RouterDTO> routers = new LinkedList<>();
        for (SysMenu menu : menus) {
            RouterDTO router = new RouterDTO();

            String path = menu.getPath();
            if (StrUtil.isEmpty(path)) {
                path = "";
            } else if (menu.getParentId() == 0 && !path.startsWith("/")) {
                path = "/" + path;
            }

            router.setPath(path);
            router.setComponent(buildComponent(menu));
            router.setName(menu.getName());
            router.setRedirect(menu.getRedirect());

            MetaDTO meta = new MetaDTO();
            meta.setTitle(menu.getTitle());
            meta.setIcon(menu.getIcon());
            meta.setHidden(menu.getHidden());
            meta.setKeepAlive(menu.getKeepAlive());
            router.setMeta(meta);

            List<SysMenu> cMenus = menu.getChildren();
            if (CollUtil.isNotEmpty(cMenus)) {
                router.setChildren(buildMenusRecursive(cMenus));
            }

            routers.add(router);
        }
        return routers;
    }

    /**
     * 构建组件路径
     * 规则：
     * 1. If type == 0 (Dir): return "ParentView"
     * 2. If type == 1 (Menu): return dbMenu.getComponent()
     */
    private String buildComponent(SysMenu menu) {
        if (MenuTypeEnum.DIR.getCode().equals(menu.getType())) {
            return UserConstants.PARENT_VIEW;
        }
        return menu.getComponent();
    }

    /**
     * 构建菜单树
     */
    private List<SysMenu> buildMenuTree(List<SysMenu> menus) {
        List<SysMenu> returnList = new ArrayList<>();
        List<Long> tempList = menus.stream().map(SysMenu::getId).collect(Collectors.toList());
        for (SysMenu menu : menus) {
            // 如果是顶级节点, 遍历该父节点的所有子节点
            if (menu.getParentId() == null || menu.getParentId() == 0 || !tempList.contains(menu.getParentId())) {
                recursionFn(menus, menu);
                returnList.add(menu);
            }
        }
        if (returnList.isEmpty()) {
            returnList = menus;
        }
        return returnList;
    }

    /**
     * 递归列表
     */
    private void recursionFn(List<SysMenu> list, SysMenu t) {
        // 得到子节点列表
        List<SysMenu> childList = getChildList(list, t);
        t.setChildren(childList);
        for (SysMenu tChild : childList) {
            if (hasChild(list, tChild)) {
                recursionFn(list, tChild);
            }
        }
    }

    /**
     * 得到子节点列表
     */
    private List<SysMenu> getChildList(List<SysMenu> list, SysMenu t) {
        List<SysMenu> tlist = new ArrayList<>();
        for (SysMenu n : list) {
            if (n.getParentId() != null && n.getParentId().equals(t.getId())) {
                tlist.add(n);
            }
        }
        return tlist;
    }

    /**
     * 判断是否有子节点
     */
    private boolean hasChild(List<SysMenu> list, SysMenu t) {
        return !getChildList(list, t).isEmpty();
    }

}
