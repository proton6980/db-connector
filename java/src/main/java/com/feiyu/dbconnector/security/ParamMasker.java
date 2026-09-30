package com.feiyu.dbconnector.security;

import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.Map;

@Singleton
public class ParamMasker {

    private static final String SENSITIVE_KEY = "(?i).*(password|passwd|secret|token|id_card|phone).*";

    public Map<String, Object> mask(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return params;
        }
        Map<String, Object> out = new HashMap<>();
        params.forEach((k, v) -> out.put(k, k.matches(SENSITIVE_KEY) ? "****" : v));
        return out;
    }
}