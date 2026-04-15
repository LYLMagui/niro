package com.niro.web.service;

import com.niro.web.dto.UnboxRecordDTO;
import com.niro.web.dto.param.UnboxRecordSaveParam;

import java.time.LocalDate;
import java.util.List;

/**
 * 开箱记录服务
 */
public interface UnboxRecordService {

    List<UnboxRecordDTO> list(Long userId, LocalDate startDate, LocalDate endDate);

    UnboxRecordDTO getDetail(Long userId, Long id);

    Long create(Long userId, UnboxRecordSaveParam param);

    void update(Long userId, Long id, UnboxRecordSaveParam param);

    void delete(Long userId, Long id);
}
