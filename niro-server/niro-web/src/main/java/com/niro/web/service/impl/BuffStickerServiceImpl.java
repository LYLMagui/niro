package com.niro.web.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.core.constant.BuffConstant;
import com.niro.core.exception.BusinessException;
import com.niro.web.dto.BuffStickerDTO;
import com.niro.web.entity.BuffScanTask;
import com.niro.web.entity.BuffSticker;
import com.niro.web.enums.TaskTypeEnum;
import com.niro.web.mapper.BuffStickerMapper;
import com.niro.web.service.BuffScanTaskService;
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

    private final BuffScanTaskService buffScanTaskService;

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
    public void syncStickers(Long userId) {
        // 权限校验：仅管理员可触发
        if (!BuffConstant.ADMIN_USER_ID.equals(userId)) {
            throw new BusinessException("仅管理员可触发印花同步任务");
        }

        log.info("用户 {} 触发印花价值同步任务", userId);

        // 检查是否已经存在正在运行的同步任务，防止重复触发
        boolean isRunning = buffScanTaskService.lambdaQuery()
                .eq(BuffScanTask::getName, "系统-印花价值自动同步")
                .eq(BuffScanTask::getStatus, BuffConstant.TASK_STATUS_RUNNING) // 运行中
                .exists();

        if (isRunning) {
            throw new BusinessException("印花同步任务正在运行中，请勿重复触发");
        }

        // 创建一个特殊的系统任务，由 niro-spider 的 TaskScanner 识别并执行
        BuffScanTask syncTask = new BuffScanTask();
        syncTask.setName("系统-印花价值自动同步");
        syncTask.setUserId(userId);
        syncTask.setTaskType(TaskTypeEnum.SYNC_STICKER.getCode()); 
        syncTask.setStatus(BuffConstant.TASK_STATUS_RUNNING);   // 立即设为运行中，由扫描器接管
        syncTask.setScanInterval(BuffConstant.DEFAULT_SYNC_INTERVAL); // 12小时间隔 (12 * 3600)
        
        buffScanTaskService.save(syncTask);
    }
}
