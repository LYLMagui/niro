package com.niro.web.service.impl;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.core.exception.BusinessException;
import com.niro.core.util.Assert;
import com.niro.core.util.RedisUtil;
import com.niro.web.constant.InviteCodeConstants;
import com.niro.web.constant.UserConstants;
import com.niro.web.dto.UserDTO;
import com.niro.web.dto.ValidateInviteCodeDTO;
import com.niro.web.dto.param.SendRegisterEmailCodeParam;
import com.niro.web.dto.param.UserLoginParam;
import com.niro.web.dto.param.UserRegisterParam;
import com.niro.web.dto.param.ValidateInviteCodeParam;
import com.niro.web.entity.InviteCode;
import com.niro.web.entity.SysRole;
import com.niro.web.entity.SysUserRole;
import com.niro.web.entity.User;
import com.niro.web.enums.UserStatusEnum;
import com.niro.web.manager.InviteCodeMapperManager;
import com.niro.web.mapper.UserMapper;
import com.niro.web.service.RegisterMailSender;
import com.niro.web.service.SysRoleService;
import com.niro.web.service.SysUserRoleService;
import com.niro.web.service.UserService;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.captcha.generator.RandomGenerator;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * 用户表 服务实现类
 * </p>
 *
 * @author liyl
 * @since 2025-12-19
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final String EMAIL_CODE_KEY_PREFIX = "niro:user:register:email-code:";
    private static final String EMAIL_CODE_COOLDOWN_PREFIX = "niro:user:register:email-code:cooldown:";
    private static final String EMAIL_CODE_DAILY_PREFIX = "niro:user:register:email-code:daily:";
    private static final String CODE_DIGITS = "0123456789";
    private static final String NICKNAME_CHARSET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private final SysRoleService sysRoleService;
    private final SysUserRoleService sysUserRoleService;
    private final InviteCodeMapperManager inviteCodeMapperManager;
    private final RegisterMailSender registerMailSender;
    private final RedisUtil redisUtil;

    @Value("${niro.register.email-code.length:6}")
    private int emailCodeLength;

    @Value("${niro.register.email-code.ttl-minutes:5}")
    private int emailCodeTtlMinutes;

    @Value("${niro.register.email-code.cooldown-seconds:60}")
    private int emailCodeCooldownSeconds;

    @Value("${niro.register.email-code.daily-limit:10}")
    private int emailCodeDailyLimit;

    @Override
    public ValidateInviteCodeDTO validateInviteCode(ValidateInviteCodeParam param) {
        String code = param.getInviteCode() == null ? "" : param.getInviteCode().trim().toUpperCase();
        if (code.isEmpty()) {
            return ValidateInviteCodeDTO.fail("邀请码不能为空");
        }
        InviteCode record = inviteCodeMapperManager.findByCode(code);
        if (record == null) {
            return ValidateInviteCodeDTO.fail("邀请码不存在");
        }
        if (!InviteCodeConstants.STATUS_ENABLED.equals(record.getStatus())) {
            return ValidateInviteCodeDTO.fail("邀请码已停用");
        }
        if (record.getExpireTime() != null && record.getExpireTime().isBefore(LocalDateTime.now())) {
            return ValidateInviteCodeDTO.fail("邀请码已过期");
        }
        if (record.getUsedUserId() != null
                && !record.getUsedUserId().equals(InviteCodeConstants.UNUSED_USER_ID)) {
            return ValidateInviteCodeDTO.fail("邀请码已被使用");
        }
        return ValidateInviteCodeDTO.ok("邀请码可用");
    }

    @Override
    public void sendRegisterEmailCode(SendRegisterEmailCodeParam param) {
        String email = param.getEmail().trim().toLowerCase();

        boolean exists = this.lambdaQuery().eq(User::getUsername, email).exists();
        Assert.isFalse(exists, "该邮箱已注册，请直接登录");

        String cooldownKey = EMAIL_CODE_COOLDOWN_PREFIX + email;
        Boolean inCooldown = redisUtil.getStringRedisTemplate().hasKey(cooldownKey);
        Assert.isFalse(Boolean.TRUE.equals(inCooldown), "验证码发送过于频繁，请稍后再试");

        String dailyKey = EMAIL_CODE_DAILY_PREFIX + email;
        Long daily = redisUtil.getStringRedisTemplate().opsForValue().increment(dailyKey);
        if (daily != null && daily == 1L) {
            redisUtil.getStringRedisTemplate().expire(dailyKey, 24, TimeUnit.HOURS);
        }
        Assert.isTrue(daily != null && daily <= emailCodeDailyLimit, "今日发送次数已达上限，请明日再试");

        String code = new RandomGenerator(CODE_DIGITS, emailCodeLength).generate();

        redisUtil.getStringRedisTemplate().opsForValue()
                .set(EMAIL_CODE_KEY_PREFIX + email, code, emailCodeTtlMinutes, TimeUnit.MINUTES);
        redisUtil.getStringRedisTemplate().opsForValue()
                .set(cooldownKey, "1", emailCodeCooldownSeconds, TimeUnit.SECONDS);

        registerMailSender.sendRegisterEmailCode(email, code, emailCodeTtlMinutes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(UserRegisterParam param) {
        String email = param.getEmail().trim().toLowerCase();
        String inviteCode = param.getInviteCode().trim().toUpperCase();
        String emailCode = param.getEmailCode().trim();

        // 1. 邀请码可用性复检（前端已校验，此处防绕过）
        InviteCode codeRecord = inviteCodeMapperManager.findByCode(inviteCode);
        assertInviteCodeUsable(codeRecord);

        // 2. 邮箱查重
        boolean exists = this.lambdaQuery().eq(User::getUsername, email).exists();
        Assert.isFalse(exists, "该邮箱已注册");

        // 3. 验证码校验
        String cacheKey = EMAIL_CODE_KEY_PREFIX + email;
        String expected = redisUtil.getStringRedisTemplate().opsForValue().get(cacheKey);
        Assert.isTrue(StrUtil.isNotBlank(expected), "验证码已过期，请重新获取");
        Assert.isTrue(expected.equals(emailCode), "验证码错误");

        // 4. 一次性使用：立即作废
        redisUtil.getStringRedisTemplate().delete(cacheKey);

        // 5. 建用户
        User user = new User();
        user.setUsername(email);
        user.setEmail(email);
        user.setPassword(BCrypt.hashpw(param.getPassword()));
        user.setStatus(UserStatusEnum.NORMAL);
        user.setNickname("_" + new RandomGenerator(NICKNAME_CHARSET, 8).generate());
        boolean saved = this.save(user);
        Assert.isTrue(saved, "注册失败，请稍后重试");

        // 6. 绑定默认角色
        SysRole defaultRole = sysRoleService.lambdaQuery()
                .eq(SysRole::getRoleKey, UserConstants.DEFAULT_ROLE_KEY)
                .eq(SysRole::getStatus, UserConstants.ROLE_STATUS_NORMAL)
                .one();
        Assert.notNull(defaultRole, "默认角色不存在，请先执行 RBAC 初始化 SQL");

        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(defaultRole.getRoleId());
        sysUserRoleService.save(userRole);

        // 7. 原子占用邀请码（并发安全兜底）
        boolean consumed = inviteCodeMapperManager.tryUse(inviteCode, user.getId());
        Assert.isTrue(consumed, "邀请码已被其他用户使用或已失效");
    }

    private void assertInviteCodeUsable(InviteCode record) {
        Assert.notNull(record, "邀请码不存在");
        Assert.isTrue(InviteCodeConstants.STATUS_ENABLED.equals(record.getStatus()), "邀请码已停用");
        Assert.isTrue(record.getExpireTime() == null || record.getExpireTime().isAfter(LocalDateTime.now()),
                "邀请码已过期");
        Assert.isTrue(record.getUsedUserId() == null
                        || record.getUsedUserId().equals(InviteCodeConstants.UNUSED_USER_ID),
                "邀请码已被使用");
    }

    @Override
    public UserDTO login(UserLoginParam param) {
        // 查询用户
        User user = this.lambdaQuery().eq(User::getUsername, param.getUsername()).one();
        Assert.notNull(user, "账号不存在");
        Assert.isTrue(UserStatusEnum.isNormal(user.getStatus()), "账号被禁用，请联系管理员");

        // 校验密码
        if (!BCrypt.checkpw(param.getPassword(), user.getPassword())) {
            throw new BusinessException("账号或密码错误");
        }
        // 登录
        StpUtil.login(user.getId());
        // 返回结果
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return userDTO;
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }

    @Override
    public UserDTO getUser(Long id) {
        Assert.notNull(id, "用户ID不能为空");

        Long currentUserId = StpUtil.getLoginIdAsLong();
        if (!UserConstants.ADMIN_ID.equals(currentUserId) && !currentUserId.equals(id)) {
            throw new BusinessException("权限不足：只能查看自己的用户信息");
        }

        User user = this.lambdaQuery().eq(User::getId, id).one();
        Assert.notNull(user, "用户不存在");
        return BeanUtil.copyProperties(user, UserDTO.class);
    }
}
