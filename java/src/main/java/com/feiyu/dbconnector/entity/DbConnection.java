package com.feiyu.dbconnector.entity;

import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.annotation.Nullable;

import java.time.LocalDateTime;

@Serdeable
@MappedEntity("db_connections")
public class DbConnection {

    @Id
    private String id;

    @MappedProperty
    private String name;

    @MappedProperty("db_type")
    private String dbType;

    private String host;

    private Integer port;

    private String username;

    private String password;

    @Nullable
    @MappedProperty("database_name")
    private String databaseName;

    @Nullable
    @MappedProperty("extra_params")
    private String extraParams;

    @MappedProperty("pool_min")
    private Integer poolMin = 2;

    @MappedProperty("pool_max")
    private Integer poolMax = 10;

    private Boolean active = true;

    @MappedProperty("created_at")
    @AutoPopulated
    private LocalDateTime createdAt;

    @MappedProperty("updated_at")
    @AutoPopulated
    private LocalDateTime updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDbType() { return dbType; }
    public void setDbType(String dbType) { this.dbType = dbType; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getDatabaseName() { return databaseName; }
    public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }
    public String getExtraParams() { return extraParams; }
    public void setExtraParams(String extraParams) { this.extraParams = extraParams; }
    public Integer getPoolMin() { return poolMin; }
    public void setPoolMin(Integer poolMin) { this.poolMin = poolMin; }
    public Integer getPoolMax() { return poolMax; }
    public void setPoolMax(Integer poolMax) { this.poolMax = poolMax; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}