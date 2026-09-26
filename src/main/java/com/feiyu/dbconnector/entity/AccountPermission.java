package com.feiyu.dbconnector.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "account_permissions")
public class AccountPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "account_id", nullable = false, length = 100)
    private String accountId;

    @Column(name = "connection_id", nullable = false, length = 36)
    private String connectionId;

    @Column(nullable = false, length = 20)
    private String permission;

    @Column(name = "allowed_tools", columnDefinition = "TEXT")
    private String allowedTools;

    @Column(name = "max_rows")
    private Integer maxRows = 100;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getConnectionId() { return connectionId; }
    public void setConnectionId(String connectionId) { this.connectionId = connectionId; }
    public String getPermission() { return permission; }
    public void setPermission(String permission) { this.permission = permission; }
    public String getAllowedTools() { return allowedTools; }
    public void setAllowedTools(String allowedTools) { this.allowedTools = allowedTools; }
    public Integer getMaxRows() { return maxRows; }
    public void setMaxRows(Integer maxRows) { this.maxRows = maxRows; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}
