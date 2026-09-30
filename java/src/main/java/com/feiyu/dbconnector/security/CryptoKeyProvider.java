package com.feiyu.dbconnector.security;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;

@Singleton
public class CryptoKeyProvider {

    private static final String KEY_FILE_NAME = ".crypto-key";
    private static final int KEY_BYTES = 32;

    private final Path keyFile;

    public CryptoKeyProvider(
            @Property(name = "dbconnector.data-dir", defaultValue = "./data")
            String dataDir) {
        this.keyFile = Path.of(dataDir).resolve(KEY_FILE_NAME);
    }

    public String resolveKey(String configuredKey) {
        if (configuredKey != null && !configuredKey.isBlank()) {
            return configuredKey;
        }
        return readOrGenerate();
    }

    private String readOrGenerate() {
        try {
            if (Files.exists(keyFile)) {
                String key = Files.readString(keyFile).trim();
                if (!key.isBlank()) {
                    return key;
                }
            }
            return generateAndSave();
        } catch (IOException e) {
            throw new IllegalStateException("读取密钥文件失败: " + keyFile, e);
        }
    }

    private String generateAndSave() throws IOException {
        byte[] raw = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(raw);
        String key = Base64.getEncoder().withoutPadding().encodeToString(raw);

        Path dir = keyFile.getParent();
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        Files.writeString(keyFile, key);

        try {
            Files.setPosixFilePermissions(keyFile,
                    PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            keyFile.toFile().setReadable(true, true);
            keyFile.toFile().setWritable(true, true);
        }

        return key;
    }
}