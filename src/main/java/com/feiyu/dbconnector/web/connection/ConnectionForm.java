package com.feiyu.dbconnector.web.connection;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ConnectionForm {

    @NotBlank(message = "名称不能为空")
    private String name;

    @NotBlank(message = "数据库类型不能为空")
    private String dbType;

    @NotBlank(message = "主机不能为空")
    private String host;

    @NotNull(message = "端口不能为空")
    @Min(value = 1, message = "端口最小为1")
    @Max(value = 65535, message = "端口最大为65535")
    private Integer port;

    @NotBlank(message = "用户名不能为空")
    private String username;

    private String password;

    private String databaseName;
    private String extraParams;

    @Min(value = 0, message = "最小池大小不能为负")
    private Integer poolMin = 2;

    @Min(value = 1, message = "最大池大小至少为1")
    private Integer poolMax = 10;

    private Boolean active = true;

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
}