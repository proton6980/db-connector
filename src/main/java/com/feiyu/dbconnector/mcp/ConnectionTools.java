package com.feiyu.dbconnector.mcp;

import com.feiyu.dbconnector.entity.DbConnection;
import com.feiyu.dbconnector.repository.DbConnectionRepository;
import io.micronaut.mcp.annotations.Tool;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class ConnectionTools {

    private final DbConnectionRepository repository;

    public ConnectionTools(DbConnectionRepository repository) {
        this.repository = repository;
    }

    public record ConnectionInfo(String id, String name, String dbType, String host, Integer port,
                                 String databaseName) {}

    @Tool(description = "列出所有可用数据库连接（ID、名称、类型、地址、库名），供其他 tool 的 connection 参数引用")
    public List<ConnectionInfo> list_connections() {
        return repository.findByActiveTrue().stream()
                .map(c -> new ConnectionInfo(c.getId(), c.getName(), c.getDbType(), c.getHost(),
                        c.getPort(), c.getDatabaseName()))
                .toList();
    }
}