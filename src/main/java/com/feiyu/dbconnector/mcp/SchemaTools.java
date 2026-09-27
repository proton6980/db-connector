package com.feiyu.dbconnector.mcp;

import com.feiyu.dbconnector.audit.Audited;
import com.feiyu.dbconnector.common.BizException;
import com.feiyu.dbconnector.common.ErrorCode;
import com.feiyu.dbconnector.service.ConnectionService;
import com.feiyu.dbconnector.service.MetadataService;
import com.feiyu.dbconnector.service.QueryService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.Map;

@Component
public class SchemaTools {

    private final ConnectionService connections;
    private final MetadataService metadataService;
    private final QueryService queryService;

    public SchemaTools(ConnectionService connections, MetadataService metadataService, QueryService queryService) {
        this.connections = connections;
        this.metadataService = metadataService;
        this.queryService = queryService;
    }

    @Tool(description = "列出指定连接的所有表（含 schema、表注释）。connection 为连接 ID 或名称。")
    @Audited
    // 返回 Object：审计切面拦截异常后返回 LLM 友好文本
    public Object list_tables(String connection) {
        try {
            return metadataService.listTables(connections.readOnlyDataSource(connections.resolve(connection)));
        } catch (SQLException e) {
            throw new BizException(ErrorCode.QUERY_FAILED, e.getMessage(), e);
        }
    }

    @Tool(description = "查看表结构：字段名/类型/可空/默认值/注释/主键/索引。table 可写 TABLE 或 SCHEMA.TABLE。")
    @Audited
    public Object describe_table(String connection, String table) {
        try {
            return metadataService.describeTable(connections.readOnlyDataSource(connections.resolve(connection)), table);
        } catch (SQLException e) {
            throw new BizException(ErrorCode.QUERY_FAILED, e.getMessage(), e);
        }
    }

    @Tool(description = "查看表样例数据（最多 5 行）。connection 为连接 ID 或名称。")
    @Audited
    public Object get_table_sample(String connection, String table) {
        return queryService.run(connections.resolve(connection), "SELECT * FROM " + table, Map.of(), 5);
    }
}
