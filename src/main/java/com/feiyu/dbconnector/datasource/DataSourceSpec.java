package com.feiyu.dbconnector.datasource;

/**
 * 动态创建 Hikari 连接池所需的最小连接描述。connectionTestQuery 为 null 时依赖驱动自校验。
 */
public record DataSourceSpec(
        String jdbcUrl,
        String username,
        String password,
        String connectionTestQuery,
        int minIdle,
        int maxPoolSize
) {}
