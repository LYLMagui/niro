package com.niro.web.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.niro.web.constant.PermissionConstants;
import com.niro.web.dto.C5InventoryPageDTO;
import com.niro.web.dto.C5InventoryRefreshResultDTO;
import com.niro.web.dto.param.C5InventoryQueryParam;
import com.niro.web.dto.param.C5InventoryRefreshParam;
import com.niro.web.service.C5InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C5 库存管理接口。
 */
@Tag(name = "C5库存管理")
@RestController
@RequestMapping("/api/c5/inventory")
@RequiredArgsConstructor
@SaCheckLogin
public class C5InventoryController {

    private final C5InventoryService c5InventoryService;

    /**
     * 刷新指定 C5 扫货账号库存。
     *
     * @param param 刷新参数
     * @return 刷新结果
     */
    @PostMapping("/refresh")
    @SaCheckPermission(PermissionConstants.C5_INVENTORY_REFRESH)
    @Operation(summary = "刷新C5库存")
    public C5InventoryRefreshResultDTO refreshInventory(@RequestBody @Valid C5InventoryRefreshParam param) {
        return c5InventoryService.refreshInventory(param);
    }

    /**
     * 分页查询 C5 库存。
     *
     * @param param 查询参数
     * @return 库存分页
     */
    @GetMapping
    @SaCheckPermission(PermissionConstants.C5_INVENTORY_LIST)
    @Operation(summary = "分页查询C5库存")
    public C5InventoryPageDTO pageInventory(@Valid C5InventoryQueryParam param) {
        return c5InventoryService.pageInventory(param);
    }
}
