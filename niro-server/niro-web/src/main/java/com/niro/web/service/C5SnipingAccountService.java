package com.niro.web.service;

import com.niro.web.dto.AppKeyPublicKeyDTO;
import com.niro.web.dto.AppKeyRevealDTO;
import com.niro.web.dto.C5SnipingAccountBalanceRefreshResultDTO;
import com.niro.web.dto.C5SnipingAccountDTO;
import com.niro.web.dto.C5SnipingAccountListDTO;
import com.niro.web.dto.param.AppKeyRevealParam;
import com.niro.web.dto.param.C5SnipingAccountBalanceRefreshParam;
import com.niro.web.dto.param.C5SnipingAccountSaveParam;
import com.niro.web.entity.C5SnipingAccount;

import java.util.List;

/**
 * C5 扫货独立账号服务。
 */
public interface C5SnipingAccountService {

    /**
     * 查询当前用户的 C5 扫货账号列表。
     *
     * @return 账号列表和余额合计
     */
    C5SnipingAccountListDTO listAccounts();

    /**
     * 查询当前用户可用于任务绑定的 C5 扫货账号列表。
     *
     * @return 可用账号列表
     */
    List<C5SnipingAccountDTO> listAvailableAccounts();

    /**
     * 保存或更新 C5 扫货账号。
     *
     * @param param 保存参数
     */
    void saveAccount(C5SnipingAccountSaveParam param);

    /**
     * 删除 C5 扫货账号。
     *
     * @param id 账号 ID
     */
    void deleteAccount(Long id);

    /**
     * 批量刷新当前用户 C5 扫货账号余额。
     *
     * @param param 余额刷新参数
     * @return 余额刷新结果列表
     */
    List<C5SnipingAccountBalanceRefreshResultDTO> refreshBalance(C5SnipingAccountBalanceRefreshParam param);

    /**
     * 按当前用户账号 ID 刷新余额。
     *
     * @param accountIds 账号 ID 列表
     * @return 余额刷新结果列表
     */
    List<C5SnipingAccountBalanceRefreshResultDTO> refreshBalanceByAccountIds(List<Long> accountIds);

    /**
     * 检测单个 C5 扫货账号配置。
     *
     * @param id 账号 ID
     */
    void checkAccount(Long id);

    /**
     * 获取 AppKey 字段加密公钥。
     *
     * @return 公钥信息
     */
    AppKeyPublicKeyDTO getAppKeyPublicKey();

    /**
     * 按需 reveal 当前用户账号 AppKey。
     *
     * @param id 账号 ID
     * @param param reveal 参数
     * @return 加密后的 AppKey 明文
     */
    AppKeyRevealDTO revealAppKey(Long id, AppKeyRevealParam param);

    /**
     * 解密账号 AppKey，用于服务端调用 C5。
     *
     * @param account 账号实体
     * @return 明文 AppKey
     */
    String decryptAccountAppKey(C5SnipingAccount account);

    /**
     * 异步刷新当前用户 C5 扫货账号余额并通过 SSE 推送。
     *
     * @param param 余额刷新参数
     */
    void refreshBalanceAsync(C5SnipingAccountBalanceRefreshParam param);
}
