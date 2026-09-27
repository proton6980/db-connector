package com.feiyu.dbconnector.security;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** 审计参数脱敏：key 命中敏感词的值替换为 ****。 */
@Component
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
