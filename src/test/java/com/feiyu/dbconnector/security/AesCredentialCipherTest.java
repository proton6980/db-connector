package com.feiyu.dbconnector.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AesCredentialCipherTest {

    private AesCredentialCipher cipher(String key) {
        MockEnvironment env = new MockEnvironment();
        if (key != null) {
            env.setProperty("dbconnector.crypto.key", key);
        }
        return new AesCredentialCipher(env);
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
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        assertThrows(IllegalStateException.class, () -> new AesCredentialCipher(env));
    }
}
