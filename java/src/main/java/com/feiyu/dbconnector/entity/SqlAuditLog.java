package com.feiyu.dbconnector.entity;

import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.annotation.Nullable;

import java.time.LocalDateTime;

@Serdeable
@MappedEntity("sql_audit_log")
public class SqlAuditLog {

    @Id
    @AutoPopulated
    private Long id;

    @MappedProperty("connection_id")
    private String connectionId;

    @MappedProperty("account_id")
    private String accountId;

    @MappedProperty("sql_text")
    private String sqlText;

    @Nullable
    private String params;

    private String status;

    @Nullable
    @MappedProperty("row_count")
    private Integer rowCount;

    @Nullable
    @MappedProperty("duration_ms")
    private Long durationMs;

    @Nullable
    @MappedProperty("error_msg")
    private String errorMsg;

    @MappedProperty("executed_at")
    @AutoPopulated
    private LocalDateTime executedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getConnectionId() { return connectionId; }
    public void setConnectionId(String connectionId) { this.connectionId = connectionId; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getSqlText() { return sqlText; }
    public void setSqlText(String sqlText) { this.sqlText = sqlText; }
    public String getParams() { return params; }
    public void setParams(String params) { this.params = params; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getRowCount() { return rowCount; }
    public void setRowCount(Integer rowCount) { this.rowCount = rowCount; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
    public LocalDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(LocalDateTime executedAt) { this.executedAt = executedAt; }
}