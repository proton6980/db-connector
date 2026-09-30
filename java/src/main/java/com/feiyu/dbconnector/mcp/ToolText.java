package com.feiyu.dbconnector.mcp;

import com.feiyu.dbconnector.service.QueryService;

import java.util.Map;

/**
 * 工具层共用的文本渲染，避免查询/EXPLAIN 各写一份。
 */
final class ToolText {

    private ToolText() {}

    static String queryText(QueryService.QueryResult r) {
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
