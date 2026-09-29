package com.feiyu.dbconnector.config;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("dbconnector.query")
public record QueryProperties(int maxRows, int timeoutSeconds) {

    public QueryProperties {
        if (maxRows <= 0) maxRows = 100;
        if (timeoutSeconds <= 0) timeoutSeconds = 10;
    }
}