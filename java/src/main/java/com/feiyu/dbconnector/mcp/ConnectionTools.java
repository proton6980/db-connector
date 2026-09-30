package com.feiyu.dbconnector.mcp;

import com.feiyu.dbconnector.entity.DbConnection;
import com.feiyu.dbconnector.repository.DbConnectionRepository;
import io.micronaut.mcp.annotations.Tool;
import jakarta.inject.Singleton;

@Singleton
public class ConnectionTools {

    private final DbConnectionRepository repository;

    public ConnectionTools(DbConnectionRepository repository) {
        this.repository = repository;
    }

    @Tool(description = "列出所有可用数据库连接（ID、名称、类型、地址、库名），供其他 tool 的 connection 参数引用")
    public String list_connections() {
        var list = repository.findByActiveTrue();
        if (list.isEmpty()) return "无可用连接";
        StringBuilder sb = new StringBuilder();
        for (DbConnection c : list) {
            sb.append(c.getId()).append(" | ").append(c.getName()).append(" | ").append(c.getDbType());
            sb.append(" | ").append(c.getHost()).append(":").append(c.getPort());
            if (c.getDatabaseName() != null) sb.append("/").append(c.getDatabaseName());
            sb.append("\n");
        }
        return sb.toString();
    }
}