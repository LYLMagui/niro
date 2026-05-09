package com.niro.web.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.niro.web.constant.PermissionConstants;
import com.niro.web.dto.Cs2GoodsOptionDTO;
import com.niro.web.service.Cs2GoodsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CS2 商品选择接口
 */
@RestController
@RequestMapping("/cs2-goods")
@RequiredArgsConstructor
@Tag(name = "CS2 商品选择")
public class Cs2GoodsController {

    private final Cs2GoodsService cs2GoodsService;

    @GetMapping("/unbox-case-options")
    @SaCheckPermission(PermissionConstants.SystemResource.GOODS_LIST)
    @Operation(summary = "获取开箱记录箱子商品选项")
    public List<Cs2GoodsOptionDTO> listUnboxCaseOptions(@RequestParam(name = "keyword", required = false) String keyword) {
        return cs2GoodsService.listUnboxCaseOptions(keyword);
    }

    @GetMapping("/c5-task-options")
    @SaCheckPermission(PermissionConstants.Task.SCAN_LIST)
    @Operation(summary = "获取 C5 任务商品选项")
    public List<Cs2GoodsOptionDTO> listC5TaskOptions(@RequestParam(name = "keyword", required = false) String keyword) {
        return cs2GoodsService.listC5TaskOptions(keyword);
    }
}
