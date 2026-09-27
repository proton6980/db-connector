package com.feiyu.dbconnector.datasource;

/**
 * 动态创建 Hikari 连接池所需的最小连接描述。connectionTestQuery 为 null 时依赖驱动自校验。
 * readOnly 为 true 时池内连接均 setReadOnly（SQL 守卫之外的 JDBC 层防御）。
 */
public record DataSourceSpec(
        String jdbcUrl,
        String username,
        String password,
        String connectionTestQuery,
        int minIdle,
        int maxPoolSize,
        boolean readOnly
) {}
