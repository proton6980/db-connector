package com.feiyu.dbconnector.mcp;

import com.feiyu.dbconnector.audit.Audited;
import com.feiyu.dbconnector.common.BizException;
import com.feiyu.dbconnector.common.ErrorCode;
import com.feiyu.dbconnector.service.ConnectionService;
import com.feiyu.dbconnector.service.MetadataService;
import com.feiyu.dbconnector.service.QueryService;
import io.micronaut.mcp.annotations.Tool;
import jakarta.inject.Singleton;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@Singleton
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
    public String list_tables(String connection) {
        try {
            List<MetadataService.TableInfo> tables = metadataService.listTables(connections.readOnlyDataSource(connections.resolve(connection)));
            if (tables.isEmpty()) return "无表";
            StringBuilder sb = new StringBuilder();
            for (MetadataService.TableInfo t : tables) {
                sb.append(t.schema()).append(".").append(t.name());
                if (t.remarks() != null && !t.remarks().isBlank()) sb.append(" — ").append(t.remarks());
                sb.append("\n");
            }
            return sb.toString();
        } catch (SQLException e) {
            throw new BizException(ErrorCode.QUERY_FAILED, e.getMessage(), e);
        }
    }

    @Tool(description = "查看表结构：字段名/类型/可空/默认值/注释/主键/索引。table 可写 TABLE 或 SCHEMA.TABLE。")
    @Audited
    public String describe_table(String connection, String table) {
        try {
            MetadataService.DescribeResult result = metadataService.describeTable(connections.readOnlyDataSource(connections.resolve(connection)), table);
            StringBuilder sb = new StringBuilder();
            sb.append("字段:\n");
            for (MetadataService.ColumnInfo c : result.columns()) {
                sb.append("  ").append(c.name()).append(" ").append(c.type());
                sb.append(c.nullable() ? " NULL" : " NOT NULL");
                if (c.primaryKey()) sb.append(" PK");
                if (c.defaultValue() != null) sb.append(" DEFAULT ").append(c.defaultValue());
                if (c.remarks() != null && !c.remarks().isBlank()) sb.append(" — ").append(c.remarks());
                sb.append("\n");
            }
            if (!result.indexes().isEmpty()) {
                sb.append("索引:\n");
                for (MetadataService.IndexInfo idx : result.indexes()) {
                    sb.append("  ").append(idx.name()).append(idx.unique() ? " UNIQUE" : "");
                    sb.append(" (").append(String.join(", ", idx.columns())).append(")\n");
                }
            }
            return sb.toString();
        } catch (SQLException e) {
            throw new BizException(ErrorCode.QUERY_FAILED, e.getMessage(), e);
        }
    }

    @Tool(description = "查看表样例数据（最多 5 行）。connection 为连接 ID 或名称。")
    @Audited
    public String get_table_sample(String connection, String table) {
        QueryService.QueryResult result = queryService.run(connections.resolve(connection), "SELECT * FROM " + table, Map.of(), 5);
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(" | ", result.columns().stream().map(c -> c.name() + "(" + c.type() + ")").toList())).append("\n");
        for (Map<String, Object> row : result.rows()) {
            sb.append(row.values().stream().map(v -> v == null ? "NULL" : v.toString())
                    .reduce((a, b) -> a + " | " + b).orElse("")).append("\n");
        }
        return sb.toString();
    }
}