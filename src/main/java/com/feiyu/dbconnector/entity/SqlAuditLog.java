package com.feiyu.dbconnector.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "sql_audit_log")
public class SqlAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "connection_id", nullable = false, length = 36)
    private String connectionId;

    @Column(name = "account_id", nullable = false, length = 100)
    private String accountId;

    @Column(name = "sql_text", nullable = false, columnDefinition = "TEXT")
    private String sqlText;

    @Column(columnDefinition = "TEXT")
    private String params;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_msg", columnDefinition = "TEXT")
    private String errorMsg;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    @PrePersist
    void onCreate() {
        executedAt = LocalDateTime.now();
    }

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
    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }
    public LocalDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(LocalDateTime executedAt) { this.executedAt = executedAt; }
}
