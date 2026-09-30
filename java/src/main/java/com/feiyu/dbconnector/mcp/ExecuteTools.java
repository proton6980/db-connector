package com.feiyu.dbconnector.mcp;

import com.feiyu.dbconnector.audit.AuditSql;
import com.feiyu.dbconnector.audit.Audited;
import com.feiyu.dbconnector.audit.PendingRowCount;
import com.feiyu.dbconnector.common.BizException;
import com.feiyu.dbconnector.common.ErrorCode;
import com.feiyu.dbconnector.entity.DbConnection;
import com.feiyu.dbconnector.service.ConnectionService;
import com.feiyu.dbconnector.service.QueryService;
import io.micronaut.mcp.annotations.Tool;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
public class ExecuteTools {

    private final ConnectionService connections;
    private final QueryService queryService;
    private final PendingRowCount pendingRows;

    public ExecuteTools(ConnectionService connections, QueryService queryService,
                        PendingRowCount pendingRows) {
        this.connections = connections;
        this.queryService = queryService;
        this.pendingRows = pendingRows;
    }

    @Tool(description = "在指定连接上执行单条写语句（INSERT/UPDATE/DELETE/MERGE），返回影响行数。"
            + "前提：该连接已在 Web 控制台开启 DML 写权限（allow_dml=true），否则会被拒绝。"
            + "参数用 :name 占位符并通过 params 绑定，禁止拼接 SQL。"
            + "UPDATE/DELETE 默认必须带 WHERE；确需全表操作时传 allowFullTable=true。"
            + "仅允许单条语句；执行后自动提交、不可回滚，谨慎操作生产库。"
            + "connection 为连接 ID 或名称（先用 list_connections 查看）。")
    @Audited
    public String execute_dml(String connection, @AuditSql String sql,
                              Map<String, Object> params, Boolean allowFullTable) {
        DbConnection c = connections.resolve(connection);
        if (!Boolean.TRUE.equals(c.getAllowDml())) {
            throw new BizException(ErrorCode.SQL_REJECTED,
                    "该连接未开启 DML 写权限（allow_dml=false），请在 Web 控制台编辑连接开启");
        }
        QueryService.WriteResult r = queryService.executeDml(c, sql, params,
                Boolean.TRUE.equals(allowFullTable));
        pendingRows.set(r.affectedRows());
        return "DML 执行完成\n语句类型: " + r.operation()
                + "\n影响行数: " + r.affectedRows()
                + "\n耗时: " + r.durationMs() + "ms";
    }

    @Tool(description = "在指定连接上执行单条 DDL（CREATE/ALTER/DROP/TRUNCATE）。"
            + "前提：该连接已在 Web 控制台开启 DDL 权限（allow_ddl=true），否则会被拒绝。"
            + "DROP/TRUNCATE 不可逆，执行前建议先用 list_tables / describe_table 确认对象。"
            + "仅允许单条 DDL，不接受 DML/SELECT。connection 为连接 ID 或名称。")
    @Audited
    public String execute_ddl(String connection, @AuditSql String sql) {
        DbConnection c = connections.resolve(connection);
        if (!Boolean.TRUE.equals(c.getAllowDdl())) {
            throw new BizException(ErrorCode.SQL_REJECTED,
                    "该连接未开启 DDL 权限（allow_ddl=false），请在 Web 控制台编辑连接开启");
        }
        long elapsed = queryService.executeDdl(c, sql);
        return "DDL 执行成功\n耗时: " + elapsed + "ms";
    }

    @Tool(description = "查看 SELECT 的执行计划。只传 SELECT 原文即可（工具自动加 EXPLAIN 前缀），"
            + "不要自己写 EXPLAIN，也禁止 EXPLAIN ANALYZE。"
            + "支持 :name 参数，不执行任何写操作。connection 为连接 ID 或名称。")
    @Audited
    public String explain_sql(String connection, @AuditSql String sql, Map<String, Object> params) {
        QueryService.QueryResult r = queryService.explain(connections.resolve(connection), sql, params);
        return ToolText.queryText(r);
    }
}
