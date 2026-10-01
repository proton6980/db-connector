package com.feiyu.dbconnector.service;

import com.feiyu.dbconnector.common.BizException;
import com.feiyu.dbconnector.common.ErrorCode;
import com.feiyu.dbconnector.config.QueryProperties;
import com.feiyu.dbconnector.entity.DbConnection;
import com.feiyu.dbconnector.security.SafetyGuardService;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import dm.jdbc.driver.DmdbStatement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.update.Update;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class QueryService {

    @Serdeable
    public record Column(String name, String type) {}

    @Serdeable
    public record QueryResult(List<Column> columns, List<Map<String, Object>> rows,
                              int rowCount, boolean truncated, long durationMs) {}

    @Serdeable
    public record WriteResult(String operation, int affectedRows, long durationMs) {}

    private static final Pattern NAMED_PARAM = Pattern.compile(":([a-zA-Z_][a-zA-Z0-9_]*)");

    private final ConnectionService connections;
    private final SafetyGuardService guard;
    private final QueryProperties props;

    public QueryService(ConnectionService connections, SafetyGuardService guard, QueryProperties props) {
        this.connections = connections;
        this.guard = guard;
        this.props = props;
    }

    public QueryResult run(DbConnection c, String sql, Map<String, Object> params, Integer maxRowsOverride) {
        String safeSql = guard.check(sql);
        DataSource ds = connections.readOnlyDataSource(c);
        int cap = (maxRowsOverride != null ? maxRowsOverride : props.maxRows()) + 1;

        ParsedSql parsed = parseNamedParams(safeSql, params == null ? Map.of() : params);

        long start = System.currentTimeMillis();
        QueryResult result;
        try (Connection conn = ds.getConnection()) {
            conn.setReadOnly(true);
            try (PreparedStatement ps = conn.prepareStatement(parsed.sql())) {
                bind(ps, parsed);
                ps.setMaxRows(cap);
                ps.setQueryTimeout(props.timeoutSeconds());
                try (ResultSet rs = ps.executeQuery()) {
                    result = extract(rs, cap);
                }
            }
        } catch (SQLTimeoutException e) {
            throw new BizException(ErrorCode.QUERY_TIMEOUT, "查询超时（>" + props.timeoutSeconds() + "s）", e);
        } catch (SQLException e) {
            throw new BizException(ErrorCode.QUERY_FAILED, e.getMessage(), e);
        }
        return new QueryResult(result.columns(), result.rows(), result.rowCount(), result.truncated(),
                System.currentTimeMillis() - start);
    }

    public WriteResult executeDml(DbConnection c, String sql, Map<String, Object> params,
                                  boolean allowFullTable) {
        SafetyGuardService.ParsedStatement p = guard.classify(sql);
        if (p.kind() != SafetyGuardService.Kind.DML) {
            throw new BizException(ErrorCode.SQL_REJECTED, "execute_dml 仅允许 INSERT/UPDATE/DELETE/MERGE");
        }
        guard.assertBulkWriteGuarded(p.statement(), allowFullTable);
        ParsedSql parsed = parseNamedParams(p.normalized(), params == null ? Map.of() : params);

        long start = System.currentTimeMillis();
        int affected;
        try (Connection conn = connections.writableDataSource(c).getConnection();
             PreparedStatement ps = conn.prepareStatement(parsed.sql())) {
            bind(ps, parsed);
            ps.setQueryTimeout(props.timeoutSeconds());
            affected = ps.executeUpdate();
        } catch (SQLTimeoutException e) {
            throw new BizException(ErrorCode.QUERY_TIMEOUT, "执行超时（>" + props.timeoutSeconds() + "s）", e);
        } catch (SQLException e) {
            throw new BizException(ErrorCode.QUERY_FAILED, e.getMessage(), e);
        }
        return new WriteResult(operationOf(p), affected, System.currentTimeMillis() - start);
    }

    public long executeDdl(DbConnection c, String sql) {
        SafetyGuardService.ParsedStatement p = guard.classify(sql);
        if (p.kind() != SafetyGuardService.Kind.DDL) {
            throw new BizException(ErrorCode.SQL_REJECTED, "execute_ddl 仅允许 CREATE/ALTER/DROP/TRUNCATE");
        }
        long start = System.currentTimeMillis();
        try (Connection conn = connections.writableDataSource(c).getConnection();
             Statement st = conn.createStatement()) {
            st.setQueryTimeout(props.timeoutSeconds());
            st.executeUpdate(p.normalized());
        } catch (SQLTimeoutException e) {
            throw new BizException(ErrorCode.QUERY_TIMEOUT, "执行超时（>" + props.timeoutSeconds() + "s）", e);
        } catch (SQLException e) {
            throw new BizException(ErrorCode.QUERY_FAILED, e.getMessage(), e);
        }
        return System.currentTimeMillis() - start;
    }

    public QueryResult explain(DbConnection c, String sql, Map<String, Object> params) {
        SafetyGuardService.ParsedStatement p = guard.classify(sql);
        if (p.kind() != SafetyGuardService.Kind.SELECT) {
            throw new BizException(ErrorCode.SQL_REJECTED,
                    "explain_sql 只接受 SELECT 原文，不要加 EXPLAIN 前缀；EXPLAIN ANALYZE 被禁止");
        }
        // 先在 SELECT 原文上转 :name -> ?，EXPLAIN 前缀由各方言路径自行添加
        ParsedSql parsed = parseNamedParams(p.normalized(), params == null ? Map.of() : params);

        long start = System.currentTimeMillis();
        QueryResult result;
        String dbType = c.getDbType() == null ? "" : c.getDbType().toUpperCase();
        try (Connection conn = connections.readOnlyDataSource(c).getConnection()) {
            conn.setReadOnly(true);
            if ("DM".equals(dbType)) {
                // DM：必须用普通 Statement；? 在 EXPLAIN 中是参数符号，不绑定，取参数化通用计划
                try (Statement st = conn.createStatement()) {
                    st.setQueryTimeout(props.timeoutSeconds());
                    st.execute("EXPLAIN " + parsed.sql());
                    result = singleTextPlan(st.unwrap(DmdbStatement.class).getExplain());
                }
            } else {
                // 标准路径（H2 / MySQL / Kingbase）：EXPLAIN 返回结果集，正常绑定
                try (PreparedStatement ps = conn.prepareStatement("EXPLAIN " + parsed.sql())) {
                    bind(ps, parsed);
                    ps.setQueryTimeout(props.timeoutSeconds());
                    try (ResultSet rs = ps.executeQuery()) {
                        result = extract(rs, props.maxRows() + 1);
                    }
                }
            }
        } catch (SQLTimeoutException e) {
            throw new BizException(ErrorCode.QUERY_TIMEOUT, "查询超时（>" + props.timeoutSeconds() + "s）", e);
        } catch (SQLException e) {
            throw new BizException(ErrorCode.QUERY_FAILED, e.getMessage(), e);
        }
        return new QueryResult(result.columns(), result.rows(), result.rowCount(), result.truncated(),
                System.currentTimeMillis() - start);
    }

    private QueryResult singleTextPlan(String plan) {
        List<Column> columns = List.of(new Column("PLAN", "VARCHAR"));
        List<Map<String, Object>> rows = List.of(Map.of("PLAN", plan == null ? "" : plan));
        return new QueryResult(columns, rows, 1, false, 0);
    }

    private String operationOf(SafetyGuardService.ParsedStatement p) {
        var s = p.statement();
        if (s instanceof Insert) return "INSERT";
        if (s instanceof Update) return "UPDATE";
        if (s instanceof Delete) return "DELETE";
        return "MERGE";
    }

    private void bind(PreparedStatement ps, ParsedSql parsed) throws SQLException {
        for (int i = 0; i < parsed.bindValues().size(); i++) {
            ps.setObject(i + 1, parsed.bindValues().get(i));
        }
    }

    private QueryResult extract(ResultSet rs, int cap) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        List<Column> columns = new ArrayList<>();
        for (int i = 1; i <= md.getColumnCount(); i++) {
            columns.add(new Column(md.getColumnLabel(i), md.getColumnTypeName(i)));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next() && rows.size() < cap) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                row.put(md.getColumnLabel(i), rs.getObject(i));
            }
            rows.add(row);
        }
        boolean truncated = rows.size() == cap;
        if (truncated) {
            rows.remove(rows.size() - 1);
        }
        return new QueryResult(columns, rows, rows.size(), truncated, 0);
    }

    private ParsedSql parseNamedParams(String sql, Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        List<Object> bindValues = new ArrayList<>();
        Matcher matcher = NAMED_PARAM.matcher(sql);
        int lastEnd = 0;
        while (matcher.find()) {
            sb.append(sql, lastEnd, matcher.start());
            String name = matcher.group(1);
            if (params.containsKey(name)) {
                sb.append("?");
                bindValues.add(params.get(name));
            } else {
                sb.append(matcher.group());
            }
            lastEnd = matcher.end();
        }
        sb.append(sql.substring(lastEnd));
        return new ParsedSql(sb.toString(), bindValues);
    }

    private record ParsedSql(String sql, List<Object> bindValues) {}
}