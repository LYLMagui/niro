package com.niro.web.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.niro.web.dto.UnboxRecordDTO;
import com.niro.web.dto.UnboxRecordPageDTO;
import com.niro.web.dto.UnboxRecordSummaryDTO;
import com.niro.web.dto.param.UnboxRecordSaveParam;

import java.time.LocalDate;
import java.util.List;

/**
 * 开箱记录服务
 */
public interface UnboxRecordService {

    Page<UnboxRecordPageDTO> page(Long userId, Integer page, Integer pageSize, LocalDate startDate, LocalDate endDate);

    UnboxRecordSummaryDTO summary(Long userId, LocalDate startDate, LocalDate endDate);

    List<UnboxRecordDTO> list(Long userId, LocalDate startDate, LocalDate endDate);

    UnboxRecordDTO getDetail(Long userId, Long id);

    Long create(Long userId, UnboxRecordSaveParam param);

    void update(Long userId, Long id, UnboxRecordSaveParam param);

    void delete(Long userId, Long id);
}
