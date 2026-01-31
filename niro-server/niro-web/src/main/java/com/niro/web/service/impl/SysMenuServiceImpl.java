package com.niro.web.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.constant.UserConstants;
import com.niro.web.dto.vo.MetaVo;
import com.niro.web.dto.vo.RouterVo;
import com.niro.web.entity.SysMenu;
import com.niro.web.entity.SysRoleMenu;
import com.niro.web.entity.SysUserRole;
import com.niro.web.enums.MenuTypeEnum;
import com.niro.web.enums.YesNoEnum;
import com.niro.web.mapper.SysMenuMapper;
import com.niro.web.mapper.SysRoleMenuMapper;
import com.niro.web.mapper.SysUserRoleMapper;
import com.niro.web.service.SysMenuService;
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

    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;

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
            List<SysUserRole> userRoles = sysUserRoleMapper.selectList(
                    new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId)
            );
            if (CollUtil.isEmpty(userRoles)) {
                return Collections.emptyList();
            }
            List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());

            // 2. 根据角色查询菜单ID
            List<SysRoleMenu> roleMenus = sysRoleMenuMapper.selectList(
                    new LambdaQueryWrapper<SysRoleMenu>().in(SysRoleMenu::getRoleId, roleIds)
            );
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
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId)
        );
        if (CollUtil.isEmpty(userRoles)) {
            return perms;
        }
        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());

        // 2. 根据角色查询菜单ID
        List<SysRoleMenu> roleMenus = sysRoleMenuMapper.selectList(
                new LambdaQueryWrapper<SysRoleMenu>().in(SysRoleMenu::getRoleId, roleIds)
        );
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
    public List<RouterVo> buildMenus(List<SysMenu> menus) {
        List<RouterVo> routers = new LinkedList<>();
        for (SysMenu menu : menus) {
            RouterVo router = new RouterVo();
            // 0=显示(YES), 1=隐藏(NO). hidden=true means 1.
            router.setHidden(YesNoEnum.NO.getCode().equals(menu.getVisible()));
            router.setName(getRouteName(menu));
            router.setPath(getRouterPath(menu));
            router.setComponent(getComponent(menu));
            router.setQuery(menu.getQuery());

            MetaVo meta = new MetaVo();
            meta.setTitle(menu.getMenuName());
            meta.setIcon(menu.getIcon());
            // 0=Cache(YES), 1=NoCache(NO). noCache=true means 1.
            meta.setNoCache(YesNoEnum.NO.getCode().equals(menu.getIsCache()));
            if (isHttp(menu.getPath())) {
                meta.setLink(menu.getPath());
            }
            router.setMeta(meta);

            List<SysMenu> cMenus = menu.getChildren();
            if (CollUtil.isNotEmpty(cMenus)) {
                router.setAlwaysShow(true);
                router.setRedirect("noRedirect");
                router.setChildren(buildMenus(cMenus));
            } else if (isMenuFrame(menu)) {
                router.setMeta(null);
                List<RouterVo> childrenList = new ArrayList<>();
                RouterVo children = new RouterVo();
                children.setPath(menu.getPath());
                children.setComponent(menu.getComponent());
                children.setName(StrUtil.upperFirst(menu.getPath()));
                children.setMeta(meta);
                childrenList.add(children);
                router.setChildren(childrenList);
            }
            routers.add(router);
        }
        return routers;
    }

    /**
     * 构建树形结构
     */
    private List<SysMenu> buildMenuTree(List<SysMenu> menus) {
        List<SysMenu> returnList = new ArrayList<>();
        List<Long> tempList = menus.stream().map(SysMenu::getMenuId).collect(Collectors.toList());

        for (SysMenu menu : menus) {
            // 如果是顶级节点（parentId为0或null，或者parentId不在当前列表中）
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
     * 获取路由名称
     */
    private String getRouteName(SysMenu menu) {
        String routerName = StrUtil.upperFirst(menu.getPath());
        // 非外链并且是一级目录（类型为目录）
        if (isMenuFrame(menu)) {
            routerName = "";
        }
        return routerName;
    }

    /**
     * 获取路由地址
     */
    private String getRouterPath(SysMenu menu) {
        String routerPath = menu.getPath();
        // 内链打开外网方式
        if (menu.getParentId() != 0 && isInnerLink(menu)) {
            routerPath = innerLinkReplaceEach(routerPath);
        }
        // 非外链并且是一级目录（类型为目录）
        if (0 == menu.getParentId() && MenuTypeEnum.DIR.getCode().equals(menu.getMenuType())
                && YesNoEnum.NO.getCode().equals(menu.getIsFrame())) {
            routerPath = "/" + menu.getPath();
        } else if (isMenuFrame(menu)) {
            routerPath = "/";
        }
        return routerPath;
    }

    /**
     * 获取组件信息
     */
    private String getComponent(SysMenu menu) {
        String component = UserConstants.LAYOUT;
        if (StrUtil.isNotEmpty(menu.getComponent()) && !isMenuFrame(menu)) {
            component = menu.getComponent();
        } else if (StrUtil.isEmpty(menu.getComponent()) && menu.getParentId() != 0 && isInnerLink(menu)) {
            component = UserConstants.INNER_LINK;
        } else if (StrUtil.isEmpty(menu.getComponent()) && isParentView(menu)) {
            component = UserConstants.PARENT_VIEW;
        }
        return component;
    }

    /**
     * 是否为菜单内部跳转
     */
    private boolean isMenuFrame(SysMenu menu) {
        return menu.getParentId() == 0 && MenuTypeEnum.MENU.getCode().equals(menu.getMenuType())
                && YesNoEnum.NO.getCode().equals(menu.getIsFrame());
    }

    /**
     * 是否为内链组件
     */
    private boolean isInnerLink(SysMenu menu) {
        return YesNoEnum.NO.getCode().equals(menu.getIsFrame()) && isHttp(menu.getPath());
    }

    /**
     * 是否为parent_view组件
     */
    private boolean isParentView(SysMenu menu) {
        return menu.getParentId() != 0 && MenuTypeEnum.DIR.getCode().equals(menu.getMenuType());
    }

    /**
     * 是否为http(s)://
     */
    private boolean isHttp(String link) {
        return StrUtil.startWithAny(link, "http://", "https://");
    }

    /**
     * 内链域名特殊字符替换
     */
    private String innerLinkReplaceEach(String path) {
        return StrUtil.replace(path, "http", "")
                .replace("https", "")
                .replace(":", "")
                .replace("//", "");
    }
}
