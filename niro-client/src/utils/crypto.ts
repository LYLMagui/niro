import CryptoJS from "crypto-js";

// 密钥（在实际生产中，建议将密钥放在环境变量中）
const SECRET_KEY = import.meta.env.VITE_CRYPTO_KEY || "niro-default-secret-key-123456";

/**
 * 加密字符串
 * @param text 需要加密的明文
 * @returns 加密后的密文
 */
export const encrypt = (text: string): string => {
  if (!text) return "";
  return CryptoJS.AES.encrypt(text, SECRET_KEY).toString();
};

/**
 * 解密字符串
 * @param cipherText 加密后的密文
 * @returns 解密后的明文
 */
export const decrypt = (cipherText: string): string => {
  if (!cipherText) return "";
  try {
    const bytes = CryptoJS.AES.decrypt(cipherText, SECRET_KEY);
    return bytes.toString(CryptoJS.enc.Utf8);
  } catch (e) {
    console.error("解密失败:", e);
    return "";
  }
};
