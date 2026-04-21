package com.niro.web.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.niro.web.dto.InviteCodeBatchCreateResultDTO;
import com.niro.web.dto.InviteCodeDetailDTO;
import com.niro.web.dto.InviteCodePageDTO;
import com.niro.web.dto.param.InviteCodeBatchCreateParam;
import com.niro.web.dto.param.InviteCodeCreateParam;
import com.niro.web.dto.param.InviteCodeQueryParam;
import com.niro.web.dto.param.InviteCodeUpdateParam;
import com.niro.web.entity.InviteCode;

import java.util.List;

/**
 * 邀请码服务接口
 */
public interface InviteCodeService extends IService<InviteCode> {

    Page<InviteCodePageDTO> pageInviteCodes(InviteCodeQueryParam param);

    InviteCodeDetailDTO getInviteCodeDetail(Long id);

    InviteCodeDetailDTO createInviteCode(Long operatorUserId, InviteCodeCreateParam param);

    InviteCodeBatchCreateResultDTO batchCreateInviteCodes(Long operatorUserId, InviteCodeBatchCreateParam param);

    InviteCodeDetailDTO updateInviteCode(Long operatorUserId, InviteCodeUpdateParam param);

    void updateStatus(Long id, Integer status);

    void batchDisable(List<Long> ids);
}
