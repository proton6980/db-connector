package com.feiyu.dbconnector.mcp;

import com.feiyu.dbconnector.audit.Audited;
import com.feiyu.dbconnector.service.ConnectionService;
import com.feiyu.dbconnector.service.QueryService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
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
    // 返回 Object：审计切面拦截异常后返回 LLM 友好文本，具体返回类型由实际值决定
    public Object query_database(String connection, String sql, Map<String, Object> params) {
        return queryService.run(connections.resolve(connection), sql, params, null);
    }
}
