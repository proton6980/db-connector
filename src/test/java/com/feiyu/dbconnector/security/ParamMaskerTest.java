package com.feiyu.dbconnector.security;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParamMaskerTest {

    private final ParamMasker masker = new ParamMasker();

    @Test
    void masksSensitiveKeys() {
        Map<String, Object> out = masker.mask(Map.of(
                "password", "x", "user_password", "x", "PHONE", "x",
                "token", "x", "idCard", "x", "name", "bob"));
        assertEquals("****", out.get("password"));
        assertEquals("****", out.get("user_password"));
        assertEquals("****", out.get("PHONE"));
        assertEquals("****", out.get("token"));
        assertEquals("bob", out.get("name"));
    }

    @Test
    void nullAndEmptyPassThrough() {
        assertEquals(null, masker.mask(null));
        assertEquals(Map.of(), masker.mask(Map.of()));
    }
}
