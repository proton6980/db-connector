package com.feiyu.dbconnector.mcp;

import com.feiyu.dbconnector.audit.Audited;
import com.feiyu.dbconnector.service.ConnectionService;
import com.feiyu.dbconnector.service.QueryService;
import io.micronaut.mcp.annotations.Tool;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
public class QueryTools {

    private final ConnectionService connections;
    private final QueryService queryService;

    public QueryTools(ConnectionService connections, QueryService queryService) {
        this.connections = connections;
        this.queryService = queryService;
    }

    @Tool(description = "在指定数据库连接上执行只读查询。仅允许单条 SELECT / WITH(CTE) 语句，"
            + "参数用 :name 占位符并通过 params 传入（绑定，不拼接）。"
            + "connection 为连接 ID 或名称（先用 list_connections 查看）。")
    @Audited
    public String query_database(String connection, String sql, Map<String, Object> params) {
        QueryService.QueryResult result = queryService.run(connections.resolve(connection), sql, params, null);
        return formatResult(result);
    }

    private String formatResult(QueryService.QueryResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("列: ");
        for (int i = 0; i < r.columns().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(r.columns().get(i).name()).append("(").append(r.columns().get(i).type()).append(")");
        }
        sb.append("\n行数: ").append(r.rowCount());
        if (r.truncated()) sb.append(" (已截断)");
        sb.append("\n耗时: ").append(r.durationMs()).append("ms\n");
        for (Map<String, Object> row : r.rows()) {
            sb.append(row.values().stream().map(v -> v == null ? "NULL" : v.toString())
                    .reduce((a, b) -> a + " | " + b).orElse("")).append("\n");
        }
        return sb.toString();
    }
}