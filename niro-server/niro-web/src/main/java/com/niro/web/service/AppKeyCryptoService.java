package com.niro.web.service;

import com.niro.web.dto.AppKeyPublicKeyDTO;

/**
 * AppKey 加密服务。
 */
public interface AppKeyCryptoService {

    /**
     * 获取前端字段级加密公钥。
     *
     * @return 公钥信息
     */
    AppKeyPublicKeyDTO getPublicKey();

    /**
     * 解密前端提交的 AppKey 密文。
     *
     * @param encryptedAppKey RSA-OAEP 密文
     * @return 明文 AppKey
     */
    String decryptTransportAppKey(String encryptedAppKey);

    /**
     * 使用前端临时公钥加密 AppKey 明文。
     *
     * @param appKey AppKey 明文
     * @param publicKey 前端临时公钥
     * @return RSA-OAEP 密文
     */
    String encryptForClient(String appKey, String publicKey);

    /**
     * 加密 AppKey 用于数据库存储。
     *
     * @param appKey AppKey 明文
     * @return AES-GCM 密文
     */
    String encryptForStorage(String appKey);

    /**
     * 解密数据库中的 AppKey 密文。
     *
     * @param encryptedAppKey AES-GCM 密文
     * @return AppKey 明文
     */
    String decryptFromStorage(String encryptedAppKey);

    /**
     * 生成 AppKey 脱敏展示值。
     *
     * @param appKey AppKey 明文
     * @return 脱敏值
     */
    String mask(String appKey);
}
