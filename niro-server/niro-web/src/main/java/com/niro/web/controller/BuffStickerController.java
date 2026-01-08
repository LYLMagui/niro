package com.niro.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.niro.web.dto.BuffStickerDTO;
import com.niro.web.service.BuffStickerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * BUFF印花价值接口
 *
 * @author liyl
 * @date 2026/01/08
 */
@Tag(name = "BUFF印花价值管理")
@RestController
@RequestMapping("/buff/sticker")
@RequiredArgsConstructor
public class BuffStickerController {

    private final BuffStickerService buffStickerService;

    @Operation(summary = "分页查询印花列表")
    @GetMapping("/page")
    public Page<BuffStickerDTO> getPage(
            @RequestParam(name = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(name = "pageSize", defaultValue = "20") Integer pageSize,
            @RequestParam(name = "keyword", required = false) String keyword) {
        return buffStickerService.getStickerPage(pageNum, pageSize, keyword);
    }

    @Operation(summary = "触发印花同步任务")
    @PostMapping("/sync")
    public void sync(@RequestParam(name = "userId") Long userId) {
        buffStickerService.syncStickers(userId);
    }
}
