package com.feiyu.dbconnector.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feiyu.dbconnector.common.BizException;
import com.feiyu.dbconnector.common.ErrorCode;
import com.feiyu.dbconnector.datasource.DataSourceSpec;
import com.feiyu.dbconnector.datasource.DynamicDataSourceManager;
import com.feiyu.dbconnector.entity.DbConnection;
import com.feiyu.dbconnector.repository.DbConnectionRepository;
import com.feiyu.dbconnector.security.AesCredentialCipher;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Service;

import java.util.Map;

/** 连接解析与目标库连接池：DbConnection 实体 → JDBC URL → Hikari 池（按 connectionId 复用）。 */
@Service
public class ConnectionService {

    private final DbConnectionRepository repository;
    private final DynamicDataSourceManager dataSourceManager;
    private final AesCredentialCipher cipher;
    private final ObjectMapper objectMapper;

    public ConnectionService(DbConnectionRepository repository, DynamicDataSourceManager dataSourceManager,
                             AesCredentialCipher cipher, ObjectMapper objectMapper) {
        this.repository = repository;
        this.dataSourceManager = dataSourceManager;
        this.cipher = cipher;
        this.objectMapper = objectMapper;
    }

    /** 按 ID 或名称解析连接；不存在 / 停用分别抛 CONNECTION_NOT_FOUND / CONNECTION_INACTIVE。 */
    public DbConnection resolve(String idOrName) {
        DbConnection c = repository.findById(idOrName).or(() -> repository.findByName(idOrName))
                .orElseThrow(() -> new BizException(ErrorCode.CONNECTION_NOT_FOUND, "连接不存在: " + idOrName));
        if (!Boolean.TRUE.equals(c.getActive())) {
            throw new BizException(ErrorCode.CONNECTION_INACTIVE, "连接已停用: " + c.getName());
        }
        return c;
    }

    /** 只读连接池（池级 readOnly，防御层之一）。 */
    public HikariDataSource readOnlyDataSource(DbConnection c) {
        String dbType = c.getDbType() == null ? "" : c.getDbType().toUpperCase();
        DataSourceSpec spec = switch (dbType) {
            case "DM" -> new DataSourceSpec(jdbcUrl(c), c.getUsername(), cipher.decrypt(c.getPassword()),
                    "SELECT 1 FROM DUAL", c.getPoolMin(), c.getPoolMax(), true);
            case "H2" -> new DataSourceSpec(jdbcUrl(c), c.getUsername(), cipher.decrypt(c.getPassword()),
                    null, c.getPoolMin(), c.getPoolMax(), true);
            default -> throw new BizException(ErrorCode.UNSUPPORTED_DB_TYPE, "暂不支持的数据库类型: " + c.getDbType());
        };
        return dataSourceManager.getOrCreate(c.getId(), spec);
    }

    private String jdbcUrl(DbConnection c) {
        String dbType = c.getDbType().toUpperCase();
        String query = extraParamsAsQuery(c.getExtraParams());
        return switch (dbType) {
            case "DM" -> "jdbc:dm://" + c.getHost() + ":" + c.getPort()
                    + (c.getDatabaseName() != null ? "/" + c.getDatabaseName() : "") + query;
            // H2 仅用于开发/测试，extraParams 不展开（H2 的属性分隔符是 ; 不是 ?）
            case "H2" -> "jdbc:h2:mem:" + (c.getDatabaseName() != null ? c.getDatabaseName() : c.getName())
                    + ";DB_CLOSE_DELAY=-1";
            default -> throw new BizException(ErrorCode.UNSUPPORTED_DB_TYPE, "暂不支持的数据库类型: " + c.getDbType());
        };
    }

    /** extraParams JSON（如 {"ssl":true}）展开为 URL query；空/非法返回空串。 */
    private String extraParamsAsQuery(String extraParams) {
        if (extraParams == null || extraParams.isBlank()) {
            return "";
        }
        try {
            Map<String, Object> params = objectMapper.readValue(extraParams, new TypeReference<>() {});
            StringBuilder sb = new StringBuilder();
            params.forEach((k, v) -> sb.append(sb.isEmpty() ? "?" : "&").append(k).append("=").append(v));
            return sb.toString();
        } catch (Exception e) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "extraParams 不是合法 JSON: " + extraParams);
        }
    }
}
