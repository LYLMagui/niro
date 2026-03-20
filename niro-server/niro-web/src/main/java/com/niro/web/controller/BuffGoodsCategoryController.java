package com.niro.web.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.niro.web.constant.PermissionConstants;
import com.niro.web.dto.BuffGoodsCategoryDTO;
import com.niro.web.service.BuffGoodsCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品分类接口
 *
 * @author liyl
 * @since 2025-12-23
 */
@Tag(name = "商品分类管理")
@RestController
@RequestMapping("/category")
@RequiredArgsConstructor
public class BuffGoodsCategoryController {

    private final BuffGoodsCategoryService buffGoodsCategoryService;

    @GetMapping("/tree")
    @SaCheckPermission(PermissionConstants.GOODS_LIST)
    @Operation(summary = "获取分类树")
    public List<BuffGoodsCategoryDTO> getCategoryTree() {
        return buffGoodsCategoryService.getCategoryTree();
    }
}
