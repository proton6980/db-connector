package com.feiyu.dbconnector.datasource;

public record DataSourceSpec(String jdbcUrl, String username, String password,
                             String connectionTestQuery, int minIdle, int maxPoolSize, boolean readOnly) {
}