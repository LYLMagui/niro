package com.niro.web.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.niro.web.constant.PermissionConstants;
import com.niro.web.dto.C5MarketPriceSnapshotReferenceDTO;
import com.niro.web.dto.param.C5MarketPriceSnapshotReferenceParam;
import com.niro.web.dto.param.C5MarketPriceSnapshotRefreshRequestParam;
import com.niro.web.service.C5MarketPriceSnapshotService;
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
 * C5 市场价格快照控制器。
 */
@Tag(name = "C5市场价格快照")
@RestController
@RequestMapping("/api/c5/market-price-snapshots")
@RequiredArgsConstructor
public class C5MarketPriceSnapshotController {

    private final C5MarketPriceSnapshotService marketPriceSnapshotService;

    /**
     * 查询本地价格参考快照。
     *
     * @param param 查询参数
     * @return 快照参考结果
     */
    @GetMapping("/reference")
    @SaCheckPermission(PermissionConstants.C5Inventory.LIST)
    @Operation(summary = "查询C5市场价格快照参考")
    public C5MarketPriceSnapshotReferenceDTO getReference(@Valid C5MarketPriceSnapshotReferenceParam param) {
        return marketPriceSnapshotService.getReference(param);
    }

    /**
     * 手动申请刷新价格快照。
     *
     * @param param 刷新参数
     * @return 快照参考结果
     */
    @PostMapping("/refresh-request")
    @SaCheckPermission(PermissionConstants.C5Inventory.LIST)
    @Operation(summary = "申请刷新C5市场价格快照")
    public C5MarketPriceSnapshotReferenceDTO requestRefresh(@RequestBody @Valid C5MarketPriceSnapshotRefreshRequestParam param) {
        return marketPriceSnapshotService.requestRefresh(param);
    }
}
