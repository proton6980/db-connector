package com.feiyu.dbconnector.security;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 凭证加解密：AES-256-GCM，密文格式 `v1:<base64(iv + ciphertext)>`，版本前缀留密钥轮换余地。
 * 密钥来源配置 dbconnector.crypto.key（env 注入），任意口令经 SHA-256 派生 32 字节密钥；
 * prod profile 缺密钥直接启动失败，dev/默认给开发密钥。
 */
@Component
public class AesCredentialCipher {

    private static final String DEV_KEY = "dev-only-key";
    private static final byte VERSION = 1;
    private static final String PREFIX = "v" + VERSION + ":";
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public AesCredentialCipher(Environment env) {
        String configured = env.getProperty("dbconnector.crypto.key", "");
        if (configured.isBlank()) {
            for (String profile : env.getActiveProfiles()) {
                if ("prod".equals(profile)) {
                    throw new IllegalStateException(
                            "prod profile 必须配置 dbconnector.crypto.key（或环境变量 DBCONNECTOR_CRYPTO_KEY）");
                }
            }
            configured = DEV_KEY;
        }
        try {
            this.key = new SecretKeySpec(
                    MessageDigest.getInstance("SHA-256").digest(configured.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (Exception e) {
            throw new IllegalStateException("密钥派生失败", e);
        }
    }

    /** 非 v1 前缀的值视为历史明文原样返回（Phase 0 遗留数据兼容）。 */
    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("凭证加密失败", e);
        }
    }

    public String decrypt(String stored) {
        if (!isEncrypted(stored)) {
            return stored;
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(GCM_TAG_BITS, all, 0, GCM_IV_BYTES));
            byte[] plain = cipher.doFinal(all, GCM_IV_BYTES, all.length - GCM_IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("凭证解密失败（密文被篡改或密钥不匹配）", e);
        }
    }
}
