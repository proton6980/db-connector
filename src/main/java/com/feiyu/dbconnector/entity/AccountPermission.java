package com.feiyu.dbconnector.entity;

import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;

@MappedEntity("account_permissions")
public class AccountPermission {

    @Id
    @AutoPopulated
    private Long id;

    @MappedProperty("account_id")
    private String accountId;

    @MappedProperty("connection_id")
    private String connectionId;

    private String role;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getConnectionId() { return connectionId; }
    public void setConnectionId(String connectionId) { this.connectionId = connectionId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}