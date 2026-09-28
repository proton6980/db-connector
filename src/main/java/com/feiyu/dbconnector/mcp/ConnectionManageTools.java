package com.feiyu.dbconnector.mcp;

import com.feiyu.dbconnector.audit.Audited;
import com.feiyu.dbconnector.entity.DbConnection;
import com.feiyu.dbconnector.service.ConnectionService;
import com.feiyu.dbconnector.web.connection.ConnectionForm;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class ConnectionManageTools {

    private final ConnectionService connectionService;

    public ConnectionManageTools(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    public record ConnectionDetail(String id, String name, String dbType, String host, Integer port,
                                   String databaseName, String username, Boolean active) {}

    @Tool(description = "创建新的数据库连接。需要提供连接 ID、名称、数据库类型、主机、端口、用户名、密码等信息。" +
            "数据库类型支持 DM（达梦）和 H2（开发测试用）。" +
            "创建后会自动加密存储密码，可通过 list_connections 查看并使用新连接。")
    @Audited
    public ConnectionDetail create_connection(
            @ToolParam(description = "连接唯一标识（英文数字下划线），如 'prod-dm'") String id,
            @ToolParam(description = "连接显示名称，如 '生产环境DM'") String name,
            @ToolParam(description = "数据库类型：DM 或 H2") String dbType,
            @ToolParam(description = "主机地址，如 192.168.1.100") String host,
            @ToolParam(description = "端口号，DM 默认 5236，H2 默认 9092") int port,
            @ToolParam(description = "数据库用户名") String username,
            @ToolParam(description = "数据库密码（敏感信息，会被加密存储）") String password,
            @ToolParam(description = "数据库名/实例名，可选，传 null 跳过") String databaseName,
            @ToolParam(description = "额外 JDBC 参数 JSON，如 {\"ssl\":true}，可选，传 null 跳过") String extraParams,
            @ToolParam(description = "连接池最小连接数，默认 2") int poolMin,
            @ToolParam(description = "连接池最大连接数，默认 10") int poolMax) {
        ConnectionForm form = new ConnectionForm();
        form.setName(name);
        form.setDbType(dbType);
        form.setHost(host);
        form.setPort(port);
        form.setUsername(username);
        form.setPassword(password);
        form.setDatabaseName(databaseName);
        form.setExtraParams(extraParams);
        form.setPoolMin(poolMin);
        form.setPoolMax(poolMax);
        form.setActive(true);
        DbConnection c = connectionService.create(id, form);
        return toDetail(c);
    }

    @Tool(description = "修改已有数据库连接配置。仅需提供要修改的字段，未提供的字段保持不变。" +
            "密码字段留空则不修改原密码。")
    @Audited
    public ConnectionDetail update_connection(
            @ToolParam(description = "要修改的连接 ID 或名称") String connection,
            @ToolParam(description = "新名称，可选，传 null 不修改") String name,
            @ToolParam(description = "新主机地址，可选，传 null 不修改") String host,
            @ToolParam(description = "新端口号，可选，传 -1 不修改") int port,
            @ToolParam(description = "新用户名，可选，传 null 不修改") String username,
            @ToolParam(description = "新密码，留空不修改，可选") String password,
            @ToolParam(description = "新数据库名，可选，传 null 不修改") String databaseName,
            @ToolParam(description = "额外参数 JSON，可选，传 null 不修改") String extraParams,
            @ToolParam(description = "最小连接数，可选，传 -1 不修改") int poolMin,
            @ToolParam(description = "最大连接数，可选，传 -1 不修改") int poolMax,
            @ToolParam(description = "是否启用，可选，传 null 不修改") Boolean active) {
        DbConnection existing = connectionService.resolve(connection);
        ConnectionForm form = new ConnectionForm();
        form.setName(name != null ? name : existing.getName());
        form.setDbType(existing.getDbType());
        form.setHost(host != null ? host : existing.getHost());
        form.setPort(port > 0 ? port : existing.getPort());
        form.setUsername(username != null ? username : existing.getUsername());
        form.setPassword(password);
        form.setDatabaseName(databaseName != null ? databaseName : existing.getDatabaseName());
        form.setExtraParams(extraParams != null ? extraParams : existing.getExtraParams());
        form.setPoolMin(poolMin >= 0 ? poolMin : existing.getPoolMin());
        form.setPoolMax(poolMax >= 0 ? poolMax : existing.getPoolMax());
        form.setActive(active != null ? active : existing.getActive());
        DbConnection c = connectionService.update(existing.getId(), form);
        return toDetail(c);
    }

    @Tool(description = "删除数据库连接并关闭其连接池。删除后该连接无法恢复，需重新创建。")
    @Audited
    public String delete_connection(
            @ToolParam(description = "要删除的连接 ID 或名称") String connection) {
        DbConnection c = connectionService.resolve(connection);
        connectionService.delete(c.getId());
        return "连接已删除: " + c.getName() + " (" + c.getId() + ")";
    }

    @Tool(description = "测试数据库连接是否可达。会用 JDBC 直连尝试执行 SELECT 1，返回成功/失败及耗时。" +
            "用于在创建连接后验证配置是否正确。")
    public String test_connection(
            @ToolParam(description = "要测试的连接 ID 或名称") String connection) {
        DbConnection c = connectionService.resolve(connection);
        ConnectionService.TestResult result = connectionService.test(c.getId());
        if (result.ok()) {
            return "连接成功: " + c.getName() + " (耗时 " + result.durationMs() + "ms)";
        }
        return "连接失败: " + c.getName() + " - " + result.message() + " (耗时 " + result.durationMs() + "ms)";
    }

    private ConnectionDetail toDetail(DbConnection c) {
        return new ConnectionDetail(c.getId(), c.getName(), c.getDbType(), c.getHost(),
                c.getPort(), c.getDatabaseName(), c.getUsername(), c.getActive());
    }
}