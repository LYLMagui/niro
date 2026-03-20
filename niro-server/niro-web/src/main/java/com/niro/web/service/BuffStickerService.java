package com.niro.web.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.niro.web.dto.BuffStickerDTO;
import com.niro.web.entity.BuffSticker;

/**
 * BUFF印花Service
 *
 * @author liyl
 * @date 2026/01/08
 */
public interface BuffStickerService extends IService<BuffSticker> {

    /**
     * 分页查询印花价值列表
     *
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @param keyword  搜索关键词(印花名称)
     * @return 分页结果
     */
    Page<BuffStickerDTO> getStickerPage(Integer pageNum, Integer pageSize, String keyword);

    /**
     * 触发印花价值同步任务，当前仅管理员可执行
     */
    void syncStickers();
}
