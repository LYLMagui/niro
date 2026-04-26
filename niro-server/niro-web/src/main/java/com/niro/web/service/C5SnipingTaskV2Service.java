package com.niro.web.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.niro.web.dto.C5SnipingBuyAttemptV2DTO;
import com.niro.web.dto.C5SnipingHitRecordV2DTO;
import com.niro.web.dto.C5SnipingTaskV2DTO;
import com.niro.web.dto.param.C5SnipingTaskV2QueryParam;
import com.niro.web.dto.param.C5SnipingTaskV2SaveParam;

public interface C5SnipingTaskV2Service {

    void createTask(C5SnipingTaskV2SaveParam param);

    void updateTask(Long id, C5SnipingTaskV2SaveParam param);

    C5SnipingTaskV2DTO getTask(Long id);

    Page<C5SnipingTaskV2DTO> pageTasks(C5SnipingTaskV2QueryParam param);

    void enableTask(Long id);

    void disableTask(Long id);

    void deleteTask(Long id);

    Page<C5SnipingHitRecordV2DTO> pageHitRecords(Long id, Long page, Long pageSize);

    Page<C5SnipingBuyAttemptV2DTO> pageBuyAttempts(Long id, Long page, Long pageSize);
}
