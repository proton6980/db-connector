package com.feiyu.dbconnector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 查询限制（全局，P1 无账号维度；RBAC 是 Phase 4 的事）。 */
@ConfigurationProperties("dbconnector.query")
public record QueryProperties(int maxRows, int timeoutSeconds) {

    public QueryProperties {
        if (maxRows <= 0) maxRows = 100;
        if (timeoutSeconds <= 0) timeoutSeconds = 10;
    }
}
