package com.feiyu.dbconnector.config;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class ConnectionForm {

    private String name;
    private String dbType;
    private String host;
    private Integer port;
    private String username;
    private String password;
    private String databaseName;
    private String extraParams;
    private Integer poolMin = 2;
    private Integer poolMax = 10;
    private Boolean active = true;
    private Boolean allowDml = false;
    private Boolean allowDdl = false;

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
    public Boolean getAllowDml() { return allowDml; }
    public void setAllowDml(Boolean allowDml) { this.allowDml = allowDml; }
    public Boolean getAllowDdl() { return allowDdl; }
    public void setAllowDdl(Boolean allowDdl) { this.allowDdl = allowDdl; }
}