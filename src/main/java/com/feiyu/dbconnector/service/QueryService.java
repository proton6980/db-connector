package com.feiyu.dbconnector.service;

import com.feiyu.dbconnector.common.BizException;
import com.feiyu.dbconnector.common.ErrorCode;
import com.feiyu.dbconnector.config.QueryProperties;
import com.feiyu.dbconnector.entity.DbConnection;
import com.feiyu.dbconnector.security.SafetyGuardService;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读查询执行链路：guard 预检 → 只读池 → maxRows/timeout 限制 → 结构化结果。
 * query_database 与 get_table_sample 共用。
 */
@Service
public class QueryService {

    public record Column(String name, String type) {}

    public record QueryResult(List<Column> columns, List<Map<String, Object>> rows,
                              int rowCount, boolean truncated, long durationMs) {}

    private final ConnectionService connections;
    private final SafetyGuardService guard;
    private final QueryProperties props;

    public QueryService(ConnectionService connections, SafetyGuardService guard, QueryProperties props) {
        this.connections = connections;
        this.guard = guard;
        this.props = props;
    }

    /**
     * @param maxRowsOverride 覆盖全局行上限（如 get_table_sample 传 5），null 用全局配置
     */
    public QueryResult run(DbConnection c, String sql, Map<String, Object> params, Integer maxRowsOverride) {
        String safeSql = guard.check(sql);
        DataSource ds = connections.readOnlyDataSource(c);
        int cap = (maxRowsOverride != null ? maxRowsOverride : props.maxRows()) + 1; // 多取 1 判截断
        JdbcTemplate jt = new JdbcTemplate(ds);
        jt.setMaxRows(cap);
        jt.setQueryTimeout(props.timeoutSeconds());

        long start = System.currentTimeMillis();
        QueryResult result;
        try {
            result = new NamedParameterJdbcTemplate(jt).query(
                    safeSql, params == null ? Map.of() : params,
                    (org.springframework.jdbc.core.ResultSetExtractor<QueryResult>) rs -> extract(rs, cap));
        } catch (DataAccessException e) {
            throw translate(e);
        }
        return new QueryResult(result.columns(), result.rows(), result.rowCount(), result.truncated(),
                System.currentTimeMillis() - start);
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
            rows.remove(rows.size() - 1); // 丢掉探测用的第 maxRows+1 行
        }
        return new QueryResult(columns, rows, rows.size(), truncated, 0);
    }

    private BizException translate(DataAccessException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof QueryTimeoutException || t instanceof SQLTimeoutException) {
                return new BizException(ErrorCode.QUERY_TIMEOUT, "查询超时（>" + props.timeoutSeconds() + "s）", e);
            }
        }
        Throwable root = e;
        while (root.getCause() != null) root = root.getCause();
        return new BizException(ErrorCode.QUERY_FAILED, root.getMessage(), e);
    }
}
