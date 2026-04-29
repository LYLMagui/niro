package com.niro.web.service.impl;

import cn.hutool.core.util.StrUtil;
import com.niro.core.exception.BusinessException;
import com.niro.web.dto.AppKeyPublicKeyDTO;
import com.niro.web.service.AppKeyCryptoService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

/**
 * AppKey 加密服务实现。
 */
@Service
public class AppKeyCryptoServiceImpl implements AppKeyCryptoService {

    private static final String ALGORITHM = "RSA-OAEP-256";
    private static final String STORAGE_PREFIX = "v1";
    private static final int AES_KEY_BYTES = 32;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${niro.security.app-key.aes-key:}")
    private String aesKeyText;

    @Value("${niro.security.app-key.rsa-public-key:}")
    private String rsaPublicKeyText;

    @Value("${niro.security.app-key.rsa-private-key:}")
    private String rsaPrivateKeyText;

    private byte[] aesKey;
    private PublicKey rsaPublicKey;
    private PrivateKey rsaPrivateKey;

    @PostConstruct
    public void init() {
        aesKey = decodeBase64(aesKeyText, "AppKey AES 密钥不能为空");
        if (aesKey.length != AES_KEY_BYTES) {
            throw new BusinessException("AppKey AES 密钥必须是32字节Base64值");
        }
        rsaPublicKey = parsePublicKey(rsaPublicKeyText);
        rsaPrivateKey = parsePrivateKey(rsaPrivateKeyText);
    }

    @Override
    public AppKeyPublicKeyDTO getPublicKey() {
        AppKeyPublicKeyDTO dto = new AppKeyPublicKeyDTO();
        dto.setAlgorithm(ALGORITHM);
        dto.setPublicKey(base64(rsaPublicKey.getEncoded()));
        return dto;
    }

    @Override
    public String decryptTransportAppKey(String encryptedAppKey) {
        if (StrUtil.isBlank(encryptedAppKey)) {
            return "";
        }
        return rsaDecrypt(encryptedAppKey, rsaPrivateKey);
    }

    @Override
    public String encryptForClient(String appKey, String publicKey) {
        PublicKey clientPublicKey = parsePublicKey(publicKey);
        return rsaEncrypt(appKey, clientPublicKey);
    }

    @Override
    public String encryptForStorage(String appKey) {
        if (StrUtil.isBlank(appKey)) {
            return "";
        }
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(appKey.getBytes(StandardCharsets.UTF_8));
            return STORAGE_PREFIX + ":" + base64(iv) + ":" + base64(encrypted);
        } catch (Exception e) {
            throw new BusinessException("AppKey加密失败");
        }
    }

    @Override
    public String decryptFromStorage(String encryptedAppKey) {
        if (StrUtil.isBlank(encryptedAppKey)) {
            return "";
        }
        String[] parts = encryptedAppKey.split(":");
        if (parts.length != 3 || !STORAGE_PREFIX.equals(parts[0])) {
            throw new BusinessException("AppKey密文格式不正确");
        }
        try {
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            byte[] encrypted = Base64.getDecoder().decode(parts[2]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException("AppKey解密失败");
        }
    }

    @Override
    public String mask(String appKey) {
        if (StrUtil.isBlank(appKey)) {
            return "";
        }
        String trimmed = appKey.trim();
        if (trimmed.length() <= 8) {
            return "****";
        }
        return trimmed.substring(0, 4) + "****" + trimmed.substring(trimmed.length() - 4);
    }

    private String rsaEncrypt(String plainText, PublicKey publicKey) {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepSha256Spec());
            return base64(cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BusinessException("AppKey传输加密失败");
        }
    }

    private String rsaDecrypt(String encryptedText, PrivateKey privateKey) {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey, oaepSha256Spec());
            return new String(cipher.doFinal(Base64.getDecoder().decode(cleanPem(encryptedText))), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException("AppKey传输解密失败");
        }
    }

    private PublicKey parsePublicKey(String publicKeyText) {
        try {
            byte[] keyBytes = decodeBase64(publicKeyText, "AppKey RSA 公钥不能为空");
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new BusinessException("AppKey RSA 公钥配置不正确");
        }
    }

    private PrivateKey parsePrivateKey(String privateKeyText) {
        try {
            byte[] keyBytes = decodeBase64(privateKeyText, "AppKey RSA 私钥不能为空");
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new BusinessException("AppKey RSA 私钥配置不正确");
        }
    }

    private byte[] decodeBase64(String text, String blankMessage) {
        if (StrUtil.isBlank(text)) {
            throw new BusinessException(blankMessage);
        }
        return Base64.getDecoder().decode(cleanPem(text));
    }

    private String cleanPem(String text) {
        return text.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
    }

    private String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private OAEPParameterSpec oaepSha256Spec() {
        return new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
    }
}
