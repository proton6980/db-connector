package com.feiyu.dbconnector.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feiyu.dbconnector.common.BizException;
import com.feiyu.dbconnector.common.ErrorCode;
import com.feiyu.dbconnector.config.ConnectionForm;
import com.feiyu.dbconnector.datasource.DataSourceSpec;
import com.feiyu.dbconnector.datasource.DynamicDataSourceManager;
import com.feiyu.dbconnector.entity.DbConnection;
import com.feiyu.dbconnector.repository.DbConnectionRepository;
import com.feiyu.dbconnector.security.AesCredentialCipher;
import com.zaxxer.hikari.HikariDataSource;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;

import java.util.UUID;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Singleton
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

    public DbConnection resolve(String idOrName) {
        DbConnection c = repository.findById(idOrName).or(() -> repository.findByName(idOrName))
                .orElseThrow(() -> new BizException(ErrorCode.CONNECTION_NOT_FOUND, "连接不存在: " + idOrName));
        if (!Boolean.TRUE.equals(c.getActive())) {
            throw new BizException(ErrorCode.CONNECTION_INACTIVE, "连接已停用: " + c.getName());
        }
        return c;
    }

    public List<DbConnection> listAll() {
        return repository.findAll();
    }

    public DbConnection getById(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.CONNECTION_NOT_FOUND, "连接不存在: " + id));
    }

    public HikariDataSource readOnlyDataSource(DbConnection c) {
        return dataSourceManager.getOrCreate(c.getId(), spec(c, false));
    }

    public HikariDataSource writableDataSource(DbConnection c) {
        return dataSourceManager.getOrCreate(c.getId() + "#rw", spec(c, true));
    }

    private DataSourceSpec spec(DbConnection c, boolean writable) {
        String dbType = c.getDbType() == null ? "" : c.getDbType().toUpperCase();
        String testQuery = switch (dbType) {
            case "DM" -> "SELECT 1 FROM DUAL";
            case "MYSQL" -> "SELECT 1";
            case "KINGBASE" -> "SELECT 1";
            case "H2" -> null;
            default -> throw new BizException(ErrorCode.UNSUPPORTED_DB_TYPE, "暂不支持的数据库类型: " + c.getDbType());
        };
        int minIdle = writable ? 0 : c.getPoolMin();
        return new DataSourceSpec(jdbcUrl(c), c.getUsername(), cipher.decrypt(c.getPassword()),
                testQuery, minIdle, c.getPoolMax(), !writable);
    }

    private void closePools(String id) {
        dataSourceManager.close(id);
        dataSourceManager.close(id + "#rw");
    }

    private String jdbcUrl(DbConnection c) {
        String dbType = c.getDbType().toUpperCase();
        String query = extraParamsAsQuery(c.getExtraParams());
        return switch (dbType) {
            case "DM" -> "jdbc:dm://" + c.getHost() + ":" + c.getPort()
                    + (c.getDatabaseName() != null ? "/" + c.getDatabaseName() : "") + query;
            case "MYSQL" -> "jdbc:mysql://" + c.getHost() + ":" + c.getPort()
                    + "/" + (c.getDatabaseName() != null ? c.getDatabaseName() : "") + query;
            case "KINGBASE" -> "jdbc:kingbase8://" + c.getHost() + ":" + c.getPort()
                    + "/" + (c.getDatabaseName() != null ? c.getDatabaseName() : "") + query;
            case "H2" -> "jdbc:h2:mem:" + (c.getDatabaseName() != null ? c.getDatabaseName() : c.getName())
                    + ";DB_CLOSE_DELAY=-1";
            default -> throw new BizException(ErrorCode.UNSUPPORTED_DB_TYPE, "暂不支持的数据库类型: " + c.getDbType());
        };
    }

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

    public DbConnection create(ConnectionForm form) {
        if (repository.existsByName(form.getName())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "连接名称已存在: " + form.getName());
        }
        DbConnection c = new DbConnection();
        c.setId(UUID.randomUUID().toString());
        applyForm(c, form);
        return repository.save(c);
    }

    public DbConnection create(String id, ConnectionForm form) {
        if (repository.existsById(id)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "连接 ID 已存在: " + id);
        }
        if (repository.existsByName(form.getName())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "连接名称已存在: " + form.getName());
        }
        DbConnection c = new DbConnection();
        c.setId(id);
        applyForm(c, form);
        return repository.save(c);
    }

    public DbConnection update(String id, ConnectionForm form) {
        DbConnection c = repository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.CONNECTION_NOT_FOUND, "连接不存在: " + id));
        if (!c.getName().equals(form.getName()) && repository.existsByName(form.getName())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "连接名称已存在: " + form.getName());
        }
        applyForm(c, form);
        c = repository.update(c);
        closePools(id);
        return c;
    }

    private void applyForm(DbConnection c, ConnectionForm form) {
        c.setName(form.getName());
        c.setDbType(form.getDbType());
        c.setHost(form.getHost());
        c.setPort(form.getPort());
        c.setUsername(form.getUsername());
        if (form.getPassword() != null && !form.getPassword().isBlank()) {
            c.setPassword(form.getPassword());
        }
        c.setDatabaseName(form.getDatabaseName());
        c.setExtraParams(form.getExtraParams());
        c.setPoolMin(form.getPoolMin());
        c.setPoolMax(form.getPoolMax());
        c.setActive(form.getActive());
        c.setAllowDml(Boolean.TRUE.equals(form.getAllowDml()));
        c.setAllowDdl(Boolean.TRUE.equals(form.getAllowDdl()));
        LocalDateTime now = LocalDateTime.now();
        if (c.getCreatedAt() == null) {
            c.setCreatedAt(now);
        }
        c.setUpdatedAt(now);
    }

    public TestResult test(String id) {
        DbConnection c = repository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.CONNECTION_NOT_FOUND, "连接不存在: " + id));
        String url = jdbcUrl(c);
        String user = c.getUsername();
        String pass = cipher.decrypt(c.getPassword());
        String testSql = switch (c.getDbType().toUpperCase()) {
            case "DM" -> "SELECT 1 FROM DUAL";
            case "MYSQL" -> "SELECT 1";
            case "KINGBASE" -> "SELECT 1";
            case "H2" -> "SELECT 1";
            default -> "SELECT 1";
        };
        long start = System.currentTimeMillis();
        try {
            DriverManager.setLoginTimeout(5);
            try (Connection conn = DriverManager.getConnection(url, user, pass)) {
                conn.createStatement().execute(testSql);
            }
            long duration = System.currentTimeMillis() - start;
            return new TestResult(true, duration, "连接成功");
        } catch (SQLException e) {
            long duration = System.currentTimeMillis() - start;
            return new TestResult(false, duration, e.getMessage());
        }
    }

    public void reload(String id) {
        closePools(id);
    }

    public void delete(String id) {
        closePools(id);
        repository.deleteById(id);
    }

    @Serdeable
    public record TestResult(boolean ok, long durationMs, String message) {}
}