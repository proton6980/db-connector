package com.feiyu.dbconnector.security;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AesCredentialCipherTest {

    private AesCredentialCipher cipher(String key) {
        Environment env = ApplicationContext.builder().build().getEnvironment();
        return new AesCredentialCipher(env, key != null ? key : "");
    }

    @Test
    void roundTrip() {
        AesCredentialCipher c = cipher("test-key");
        String ct = c.encrypt("SYSDBA001");
        assertTrue(ct.startsWith("v1:"));
        assertNotEquals("SYSDBA001", ct);
        assertEquals("SYSDBA001", c.decrypt(ct));
    }

    @Test
    void tamperedCiphertextFails() {
        AesCredentialCipher c = cipher("test-key");
        String ct = c.encrypt("SYSDBA001");
        String tampered = ct.substring(0, ct.length() - 2) + "A=";
        assertThrows(IllegalStateException.class, () -> c.decrypt(tampered));
    }

    @Test
    void wrongKeyFails() {
        String ct = cipher("key-one").encrypt("SYSDBA001");
        assertThrows(IllegalStateException.class, () -> cipher("key-two").decrypt(ct));
    }

    @Test
    void legacyPlaintextPassesThrough() {
        assertEquals("plain", cipher("test-key").decrypt("plain"));
    }

    @Test
    void missingKeyFailsInProdProfile() {
        Environment env = ApplicationContext.builder().environments("prod").build().getEnvironment();
        assertThrows(IllegalStateException.class, () -> new AesCredentialCipher(env, ""));
    }
}