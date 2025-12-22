package com.niro.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.niro.web.dto.BuffGoodsDTO;
import com.niro.web.dto.BuffGoodsSimpleDTO;
import com.niro.web.dto.param.GoodsQueryParam;
import com.niro.web.entity.BuffGoods;
import com.niro.web.service.BuffGoodsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 商品表 前端控制器
 * </p>
 *
 * @author liyl
 * @since 2025-12-22
 */
@RestController
@RequestMapping("/goods")
@RequiredArgsConstructor
@Tag(name = "商品管理", description = "商品服务相关接口")
public class BuffGoodsController {

    private final BuffGoodsService buffGoodsService;
    

// ...

    @GetMapping("/page")
    @Operation(summary = "分页查询商品列表")
    public Page<BuffGoodsDTO> queryGoodsPage(@Valid GoodsQueryParam param) {
        Page<BuffGoods> page = new Page<>(param.getPage(), param.getPageSize());
        return buffGoodsService.queryGoodsPage(page, param);
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获取商品简单列表(支持搜索)")
    public List<BuffGoodsSimpleDTO> getSimpleList(@RequestParam(name = "keyword", required = false) String keyword) {
        return buffGoodsService.getSimpleList(keyword);
    }
}