package com.feiyu.dbconnector.web.api;

import com.feiyu.dbconnector.entity.DbConnection;

import java.time.LocalDateTime;

/** API 连接响应，刻意省略 password。 */
public record ConnectionResponse(
        String id,
        String name,
        String dbType,
        String host,
        Integer port,
        String username,
        String databaseName,
        String extraParams,
        Integer poolMin,
        Integer poolMax,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ConnectionResponse from(DbConnection c) {
        return new ConnectionResponse(
                c.getId(),
                c.getName(),
                c.getDbType(),
                c.getHost(),
                c.getPort(),
                c.getUsername(),
                c.getDatabaseName(),
                c.getExtraParams(),
                c.getPoolMin(),
                c.getPoolMax(),
                c.getActive(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
