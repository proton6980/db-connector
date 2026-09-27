package com.feiyu.dbconnector.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 挂在 DbConnection.password 上的 JPA 转换器：实体持明文，落库自动加密、读库自动解密。 */
@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final AesCredentialCipher cipher;

    @Autowired
    public EncryptedStringConverter(AesCredentialCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute == null || cipher.isEncrypted(attribute) ? attribute : cipher.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return dbData == null ? null : cipher.decrypt(dbData);
    }
}
