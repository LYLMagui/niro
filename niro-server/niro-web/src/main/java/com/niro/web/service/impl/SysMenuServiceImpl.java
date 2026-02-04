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
                    .in(SysMenu::getMenuType, MenuTypeEnum.DIR.getCode(), MenuTypeEnum.MENU.getCode())
                    .eq(SysMenu::getStatus, YesNoEnum.YES.getCode())
                    .orderByAsc(SysMenu::getOrderNum)
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
                    .in(SysMenu::getMenuId, menuIds)
                    .in(SysMenu::getMenuType, MenuTypeEnum.DIR.getCode(), MenuTypeEnum.MENU.getCode())
                    .eq(SysMenu::getStatus, YesNoEnum.YES.getCode())
                    .orderByAsc(SysMenu::getOrderNum)
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
                .in(SysMenu::getMenuId, menuIds)
                .eq(SysMenu::getStatus, YesNoEnum.YES.getCode())
                .list();

        for (SysMenu menu : menus) {
            if (StrUtil.isNotEmpty(menu.getPerms())) {
                perms.add(menu.getPerms());
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
        return buildMenusRecursive(menus, "");
    }

    /**
     * 递归构建路由
     * 大厂规范：
     * - path: 绝对路径（以 / 开头）
     * - component: 优先使用 component_path，其次根据菜单类型判断
     * - name: 由 path 自动推导（首字母大写）
     */
    private List<RouterDTO> buildMenusRecursive(List<SysMenu> menus, String parentPath) {
        List<RouterDTO> routers = new LinkedList<>();
        for (SysMenu menu : menus) {
            RouterDTO router = new RouterDTO();

            String path = menu.getPath();
            if (StrUtil.isEmpty(path)) {
                path = "";
            } else if (menu.getParentId() == 0 && !path.startsWith("/") && !isHttp(path)) {
                path = "/" + path;
            }

            router.setPath(path);
            router.setComponent(buildComponent(menu));
            router.setHidden(YesNoEnum.NO.getCode().equals(menu.getVisible()));
            router.setName(buildRouteName(path));
            router.setQuery(menu.getQuery());

            MetaDTO meta = new MetaDTO();
            meta.setTitle(menu.getMenuName());
            meta.setIcon(menu.getIcon());
            meta.setNoCache(YesNoEnum.NO.getCode().equals(menu.getIsCache()));
            meta.setBreadcrumb(YesNoEnum.YES.getCode().equals(menu.getBreadcrumb()));
            if (isHttp(path)) {
                meta.setLink(path);
            }
            router.setMeta(meta);

            List<SysMenu> cMenus = menu.getChildren();
            if (CollUtil.isNotEmpty(cMenus)) {
                router.setAlwaysShow(true);
                router.setRedirect("noRedirect");
                router.setChildren(buildMenusRecursive(cMenus, path));
            } else if (MenuTypeEnum.MENU.getCode().equals(menu.getMenuType())) {
                router.setMeta(null);
                List<RouterDTO> childrenList = new ArrayList<>();
                RouterDTO children = new RouterDTO();
                children.setPath("");
                children.setMeta(meta);
                childrenList.add(children);
                router.setChildren(childrenList);
            }

            routers.add(router);
        }
        return routers;
    }

    /**
     * 构建组件路径
     * 优先级：
     * 1. 一级菜单（parentId=0） -> Layout
     * 2. component_path（显式存储）
     * 3. 目录类型（DIR） -> ParentView
     * 4. 叶子节点 -> 空字符串（前端根据 path 推导）
     */
    private String buildComponent(SysMenu menu) {
        if (menu.getParentId() == 0) {
            return UserConstants.LAYOUT;
        }
        if (StrUtil.isNotEmpty(menu.getComponentPath())) {
            return menu.getComponentPath();
        }
        if (MenuTypeEnum.DIR.getCode().equals(menu.getMenuType())) {
            return UserConstants.PARENT_VIEW;
        }
        return "";
    }

    /**
     * 构建路由名称
     * /dashboard -> Dashboard
     * /system/goods -> Systemgoods
     */
    private String buildRouteName(String path) {
        if (StrUtil.isEmpty(path) || "/".equals(path)) {
            return "";
        }
        String name = path.startsWith("/") ? path.substring(1) : path;
        return StrUtil.upperFirst(name.replace("/", ""));
    }

    /**
     * 构建树形结构
     */
    private List<SysMenu> buildMenuTree(List<SysMenu> menus) {
        List<SysMenu> returnList = new ArrayList<>();
        List<Long> tempList = menus.stream().map(SysMenu::getMenuId).collect(Collectors.toList());

        for (SysMenu menu : menus) {
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
            if (n.getParentId() != null && n.getParentId().equals(t.getMenuId())) {
                tlist.add(n);
            }
        }
        return tlist;
    }

    /**
     * 判断是否有子节点
     */
    private boolean hasChild(List<SysMenu> list, SysMenu t) {
        return CollUtil.isNotEmpty(getChildList(list, t));
    }

    /**
     * 是否为http(s)://
     */
    private boolean isHttp(String link) {
        return StrUtil.startWithAny(link, "http://", "https://");
    }

}
