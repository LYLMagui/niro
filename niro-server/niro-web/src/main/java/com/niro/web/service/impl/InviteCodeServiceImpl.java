package com.niro.web.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.core.util.Assert;
import com.niro.web.constant.InviteCodeConstants;
import com.niro.web.dto.InviteCodeBatchCreateResultDTO;
import com.niro.web.dto.InviteCodeDetailDTO;
import com.niro.web.dto.InviteCodePageDTO;
import com.niro.web.dto.param.InviteCodeBatchCreateParam;
import com.niro.web.dto.param.InviteCodeCreateParam;
import com.niro.web.dto.param.InviteCodeQueryParam;
import com.niro.web.dto.param.InviteCodeUpdateParam;
import com.niro.web.entity.InviteCode;
import com.niro.web.entity.User;
import com.niro.web.manager.InviteCodeMapperManager;
import com.niro.web.mapper.InviteCodeMapper;
import com.niro.web.service.InviteCodeService;
import com.niro.web.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 邀请码服务实现
 */
@Service
@RequiredArgsConstructor
public class InviteCodeServiceImpl extends ServiceImpl<InviteCodeMapper, InviteCode> implements InviteCodeService {


    private final InviteCodeMapperManager inviteCodeMapperManager;
    private final UserService userService;

    @Override
    public Page<InviteCodePageDTO> pageInviteCodes(InviteCodeQueryParam param) {
        LocalDateTime startDateTime = parseStartDate(param.getStartDate());
        LocalDateTime endDateTime = parseEndDate(param.getEndDate());

        List<InviteCode> records = inviteCodeMapperManager.lambdaQuery()
                .eq(param.getStatus() != null, InviteCode::getStatus, param.getStatus())
                .eq(param.getIssuerUserId() != null, InviteCode::getIssuerUserId, param.getIssuerUserId())
                .ge(startDateTime != null, InviteCode::getCreatedAt, startDateTime)
                .le(endDateTime != null, InviteCode::getCreatedAt, endDateTime)
                .orderByDesc(InviteCode::getCreatedAt)
                .list();

        List<User> relatedUsers = loadRelatedUsers(records);
        List<InviteCodePageDTO> filtered = records.stream()
                .filter(record -> matchAvailability(record, param.getAvailability()))
                .filter(record -> matchKeyword(record, param.getKeyword(), relatedUsers))
                .map(record -> toPageDTO(record, relatedUsers))
                .collect(Collectors.toList());

        long pageNum = param.getPage();
        long pageSize = param.getPageSize();
        int fromIndex = (int) Math.min((pageNum - 1) * pageSize, filtered.size());
        int toIndex = (int) Math.min(fromIndex + pageSize, filtered.size());
        Page<InviteCodePageDTO> dtoPage = new Page<>(pageNum, pageSize, filtered.size());
        dtoPage.setRecords(filtered.subList(fromIndex, toIndex));
        return dtoPage;
    }

    @Override
    public InviteCodeDetailDTO getInviteCodeDetail(Long id) {
        InviteCode record = inviteCodeMapperManager.getById(id);
        Assert.notNull(record, "邀请码不存在");
        return toDetailDTO(record, loadRelatedUsers(List.of(record)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InviteCodeDetailDTO createInviteCode(Long operatorUserId, InviteCodeCreateParam param) {
        InviteCode record = new InviteCode();
        record.setCode(resolveCode(param.getCode(), null));
        record.setIssuerUserId(operatorUserId == null ? InviteCodeConstants.SYSTEM_USER_ID : operatorUserId);
        record.setMaxUseCount(1);
        record.setUsedCount(0);
        record.setStatus(InviteCodeConstants.STATUS_ENABLED);
        record.setUsedUserId(InviteCodeConstants.UNUSED_USER_ID);
        record.setUsedAt(InviteCodeConstants.UNUSED_AT);
        record.setExpireTime(resolveExpireTime(param.getForever(), param.getExpireTime()));
        record.setRemark(StrUtil.blankToDefault(StrUtil.trim(param.getRemark()), ""));
        boolean saved = this.save(record);
        Assert.isTrue(saved, "新建邀请码失败");
        return toDetailDTO(record, loadRelatedUsers(List.of(record)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InviteCodeBatchCreateResultDTO batchCreateInviteCodes(Long operatorUserId, InviteCodeBatchCreateParam param) {
        Assert.notNull(param.getQuantity(), "生成数量不能为空");
        String prefix = normalizePrefix(param.getPrefix());
        Set<String> generatedCodes = new LinkedHashSet<>();
        List<InviteCode> records = new ArrayList<>();
        for (int i = 0; i < param.getQuantity(); i++) {
            InviteCode record = new InviteCode();
            record.setCode(generateUniqueCode(prefix, generatedCodes));
            record.setIssuerUserId(operatorUserId == null ? InviteCodeConstants.SYSTEM_USER_ID : operatorUserId);
            record.setMaxUseCount(1);
            record.setUsedCount(0);
            record.setStatus(InviteCodeConstants.STATUS_ENABLED);
            record.setUsedUserId(InviteCodeConstants.UNUSED_USER_ID);
            record.setUsedAt(InviteCodeConstants.UNUSED_AT);
            record.setExpireTime(resolveExpireTime(param.getForever(), param.getExpireTime()));
            record.setRemark(StrUtil.blankToDefault(StrUtil.trim(param.getRemark()), ""));
            records.add(record);
        }
        boolean saved = this.saveBatch(records);
        Assert.isTrue(saved, "批量生成邀请码失败");

        InviteCodeBatchCreateResultDTO result = new InviteCodeBatchCreateResultDTO();
        result.setRecords(records.stream().map(record -> {
            InviteCodeBatchCreateResultDTO.InviteCodeCreatedDTO dto = new InviteCodeBatchCreateResultDTO.InviteCodeCreatedDTO();
            dto.setId(record.getId());
            dto.setCode(record.getCode());
            dto.setForever(isForever(record.getExpireTime()));
            dto.setExpireTime(record.getExpireTime());
            dto.setRemark(record.getRemark());
            return dto;
        }).collect(Collectors.toList()));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InviteCodeDetailDTO updateInviteCode(Long operatorUserId, InviteCodeUpdateParam param) {
        InviteCode record = inviteCodeMapperManager.getById(param.getId());
        Assert.notNull(record, "邀请码不存在");
        boolean used = isUsed(record);
        if (used) {
            Assert.isTrue(param.getExpireTime() == null && !Boolean.TRUE.equals(param.getForever()), "已使用邀请码不能修改有效期");
        } else if (Boolean.TRUE.equals(param.getForever()) || param.getExpireTime() != null) {
            record.setExpireTime(resolveExpireTime(param.getForever(), param.getExpireTime()));
        }
        record.setRemark(StrUtil.blankToDefault(StrUtil.trim(param.getRemark()), ""));
        boolean updated = this.updateById(record);
        Assert.isTrue(updated, "更新邀请码失败");
        return toDetailDTO(record, loadRelatedUsers(List.of(record)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        InviteCode record = inviteCodeMapperManager.getById(id);
        Assert.notNull(record, "邀请码不存在");
        Assert.isTrue(!isUsed(record), "已使用邀请码不能修改状态");
        Assert.isTrue(status != null && (status == 0 || status == 1), "邀请码状态非法");
        record.setStatus(status);
        boolean updated = this.updateById(record);
        Assert.isTrue(updated, "更新邀请码状态失败");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDisable(List<Long> ids) {
        Assert.notEmpty(ids, "邀请码ID列表不能为空");
        List<InviteCode> records = inviteCodeMapperManager.lambdaQuery().in(InviteCode::getId, ids).list();
        Assert.notEmpty(records, "邀请码不存在");
        records.forEach(record -> Assert.isTrue(!isUsed(record), "包含已使用邀请码，无法批量停用"));
        List<InviteCode> updates = records.stream().map(record -> {
            InviteCode item = new InviteCode();
            item.setId(record.getId());
            item.setStatus(0);
            return item;
        }).collect(Collectors.toList());
        boolean updated = this.updateBatchById(updates);
        Assert.isTrue(updated, "批量停用邀请码失败");
    }

    private InviteCodePageDTO toPageDTO(InviteCode record, List<User> users) {
        InviteCodePageDTO dto = BeanUtil.copyProperties(record, InviteCodePageDTO.class);
        dto.setAvailability(resolveAvailability(record));
        dto.setCreatorName(resolveCreatorName(record.getIssuerUserId(), users));
        dto.setForever(isForever(record.getExpireTime()));
        if (!isUsed(record)) {
            dto.setUsedUserId(null);
            dto.setUsedAt(null);
        }
        fillRegistrationFields(dto, record, users);
        return dto;
    }

    private InviteCodeDetailDTO toDetailDTO(InviteCode record, List<User> users) {
        InviteCodeDetailDTO dto = BeanUtil.copyProperties(record, InviteCodeDetailDTO.class);
        dto.setAvailability(resolveAvailability(record));
        dto.setCreatorName(resolveCreatorName(record.getIssuerUserId(), users));
        dto.setForever(isForever(record.getExpireTime()));
        if (!isUsed(record)) {
            dto.setUsedUserId(null);
            dto.setUsedAt(null);
        }
        fillRegistrationFields(dto, record, users);
        return dto;
    }

    private void fillRegistrationFields(Object target, InviteCode record, List<User> users) {
        User usedUser = findUser(users, record.getUsedUserId());
        String statusText = usedUser == null ? null : usedUser.getStatus().getDescription();
        if (target instanceof InviteCodePageDTO dto) {
            dto.setRegistrationNickname(usedUser == null ? null : usedUser.getNickname());
            dto.setRegistrationEmail(usedUser == null ? null : usedUser.getEmail());
            dto.setRegistrationAccountStatus(statusText);
        }
        if (target instanceof InviteCodeDetailDTO dto) {
            dto.setRegistrationNickname(usedUser == null ? null : usedUser.getNickname());
            dto.setRegistrationEmail(usedUser == null ? null : usedUser.getEmail());
            dto.setRegistrationAccountStatus(statusText);
        }
    }

    private List<User> loadRelatedUsers(List<InviteCode> records) {
        Set<Long> userIds = new LinkedHashSet<>();
        records.stream().map(InviteCode::getIssuerUserId).filter(id -> id != null && id > 0).forEach(userIds::add);
        records.stream().map(InviteCode::getUsedUserId).filter(id -> id != null && id > 0).forEach(userIds::add);
        if (userIds.isEmpty()) {
            return List.of();
        }
        return userService.lambdaQuery().in(User::getId, userIds).list();
    }

    private User findUser(List<User> users, Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        return users.stream().filter(item -> userId.equals(item.getId())).findFirst().orElse(null);
    }

    private String resolveCreatorName(Long issuerUserId, List<User> users) {
        if (issuerUserId == null || issuerUserId <= 0) {
            return "系统";
        }
        User user = findUser(users, issuerUserId);
        return user == null ? String.valueOf(issuerUserId) : user.getNickname();
    }

    private boolean matchAvailability(InviteCode record, String availability) {
        if (StrUtil.isBlank(availability)) {
            return true;
        }
        return StrUtil.equals(resolveAvailability(record), availability);
    }

    private boolean matchKeyword(InviteCode record, String keyword, List<User> users) {
        if (StrUtil.isBlank(keyword)) {
            return true;
        }
        String lowerKeyword = keyword.trim().toLowerCase();
        User usedUser = findUser(users, record.getUsedUserId());
        String creatorName = resolveCreatorName(record.getIssuerUserId(), users).toLowerCase();
        return record.getCode().toLowerCase().contains(lowerKeyword)
                || StrUtil.blankToDefault(record.getRemark(), "").toLowerCase().contains(lowerKeyword)
                || creatorName.contains(lowerKeyword)
                || (usedUser != null && (
                StrUtil.blankToDefault(usedUser.getNickname(), "").toLowerCase().contains(lowerKeyword)
                        || StrUtil.blankToDefault(usedUser.getEmail(), "").toLowerCase().contains(lowerKeyword)));
    }

    private String resolveAvailability(InviteCode record) {
        if (isUsed(record)) {
            return InviteCodeConstants.AVAILABILITY_USED;
        }
        if (!InviteCodeConstants.STATUS_ENABLED.equals(record.getStatus())) {
            return InviteCodeConstants.AVAILABILITY_DISABLED;
        }
        if (record.getExpireTime() != null && record.getExpireTime().isBefore(LocalDateTime.now())) {
            return InviteCodeConstants.AVAILABILITY_EXPIRED;
        }
        return InviteCodeConstants.AVAILABILITY_AVAILABLE;
    }

    private boolean isUsed(InviteCode record) {
        return record.getUsedUserId() != null && !record.getUsedUserId().equals(InviteCodeConstants.UNUSED_USER_ID);
    }

    private String resolveCode(String manualCode, String prefix) {
        if (StrUtil.isNotBlank(manualCode)) {
            String normalized = normalizeCode(manualCode);
            Assert.isTrue(normalized.matches("^[A-Z0-9]{10}$"), "邀请码必须为 10 位大写字母或数字");
            ensureCodeUnique(normalized, null);
            return normalized;
        }
        return generateUniqueCode(prefix);
    }

    private String generateUniqueCode(String prefix) {
        return generateUniqueCode(prefix, new LinkedHashSet<>());
    }

    private String generateUniqueCode(String prefix, Set<String> generatedCodes) {
        String normalizedPrefix = normalizePrefix(prefix);
        for (int attempt = 0; attempt < 50; attempt++) {
            String candidate = normalizedPrefix + randomCode(InviteCodeConstants.INVITE_CODE_LENGTH - normalizedPrefix.length());
            if (!generatedCodes.contains(candidate) && inviteCodeMapperManager.findByCode(candidate) == null) {
                generatedCodes.add(candidate);
                return candidate;
            }
        }
        Assert.isTrue(false, "生成邀请码失败，请稍后重试");
        return "";
    }

    private String randomCode(int length) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int index = (int) (Math.random() * InviteCodeConstants.INVITE_CODE_CHARS.length());
            builder.append(InviteCodeConstants.INVITE_CODE_CHARS.charAt(index));
        }
        return builder.toString();
    }

    private void ensureCodeUnique(String code, Long currentId) {
        InviteCode existing = inviteCodeMapperManager.findByCode(code);
        Assert.isTrue(existing == null || existing.getId().equals(currentId), "邀请码已存在");
    }

    private String normalizeCode(String code) {
        return StrUtil.trimToEmpty(code).toUpperCase();
    }

    private String normalizePrefix(String prefix) {
        String normalized = StrUtil.trimToEmpty(prefix).toUpperCase();
        if (StrUtil.isBlank(normalized)) {
            return "";
        }
        Assert.isTrue(normalized.matches("^[A-Z0-9]{1,9}$"), "前缀必须为 1 到 9 位大写字母或数字");
        return normalized;
    }

    private boolean isForever(LocalDateTime expireTime) {
        return expireTime != null && expireTime.getYear() >= 9999;
    }

    private LocalDateTime resolveExpireTime(Boolean forever, LocalDateTime expireTime) {
        if (Boolean.TRUE.equals(forever)) {
            return InviteCodeConstants.FOREVER_EXPIRE_TIME;
        }
        Assert.notNull(expireTime, "过期时间不能为空");
        return expireTime;
    }

    private LocalDateTime parseStartDate(String startDate) {
        if (StrUtil.isBlank(startDate)) {
            return null;
        }
        return LocalDate.parse(startDate).atStartOfDay();
    }

    private LocalDateTime parseEndDate(String endDate) {
        if (StrUtil.isBlank(endDate)) {
            return null;
        }
        return LocalDate.parse(endDate).plusDays(1).atStartOfDay().minusSeconds(1);
    }

}
