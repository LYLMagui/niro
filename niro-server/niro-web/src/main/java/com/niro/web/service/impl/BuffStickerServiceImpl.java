package com.niro.web.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.core.constant.GlobalConstant;
import com.niro.core.exception.BusinessException;
import com.niro.web.dto.BuffStickerDTO;
import com.niro.web.entity.BuffSticker;
import com.niro.web.mapper.BuffStickerMapper;
import com.niro.web.service.BuffStickerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * BUFF印花Service实现类
 *
 * @author liyl
 * @date 2026/01/08
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BuffStickerServiceImpl extends ServiceImpl<BuffStickerMapper, BuffSticker> implements BuffStickerService {

    @Override
    public Page<BuffStickerDTO> getStickerPage(Integer pageNum, Integer pageSize, String keyword) {
        Page<BuffSticker> page = lambdaQuery()
                .like(StrUtil.isNotBlank(keyword), BuffSticker::getName, keyword)
                .orderByDesc(BuffSticker::getPrice)
                .page(new Page<>(pageNum, pageSize));

        Page<BuffStickerDTO> dtoPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<BuffStickerDTO> dtoList = BeanUtil.copyToList(page.getRecords(), BuffStickerDTO.class);
        dtoPage.setRecords(dtoList);
        return dtoPage;
    }

    @Override
    public void syncStickers() {
        Long userId = StpUtil.getLoginIdAsLong();

        if (!GlobalConstant.ADMIN_USER_ID.equals(userId)) {
            throw new BusinessException("仅管理员可触发印花同步任务");
        }

        log.info("用户 {} 尝试触发印花价值同步任务，但简化版已移除该链路", userId);
        throw new BusinessException("简化版已移除印花同步任务，请使用现有印花数据");
    }
}
