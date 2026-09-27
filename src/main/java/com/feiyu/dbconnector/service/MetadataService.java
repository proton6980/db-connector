package com.feiyu.dbconnector.service;

import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 元数据读取：JDBC DatabaseMetaData（表清单/字段/主键/索引/注释）。 */

@Service
public class MetadataService {

    /** DM 系统内置 schema（SYSDBA 是默认用户 schema，不能滤）。 */
    private static final Set<String> DM_SYSTEM_SCHEMAS = Set.of("SYS", "SYSSSO", "SYSAUDITOR", "CTI_SYSDBA");

    public record TableInfo(String schema, String name, String remarks) {}

    public record ColumnInfo(String name, String type, boolean nullable, String defaultValue,
                             String remarks, boolean primaryKey) {}

    public record IndexInfo(String name, boolean unique, List<String> columns) {}

    public record DescribeResult(List<ColumnInfo> columns, List<IndexInfo> indexes) {}

    public List<TableInfo> listTables(DataSource ds) throws SQLException {
        try (Connection conn = ds.getConnection();
             ResultSet rs = conn.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            List<TableInfo> tables = new ArrayList<>();
            while (rs.next()) {
                String schema = rs.getString("TABLE_SCHEM");
                if (DM_SYSTEM_SCHEMAS.contains(schema)) {
                    continue;
                }
                tables.add(new TableInfo(schema, rs.getString("TABLE_NAME"), rs.getString("REMARKS")));
            }
            return tables;
        }
    }

    public DescribeResult describeTable(DataSource ds, String table) throws SQLException {
        String schema = null;
        String name = table;
        int dot = table.indexOf('.');
        if (dot > 0) {
            schema = table.substring(0, dot);
            name = table.substring(dot + 1);
        }
        try (Connection conn = ds.getConnection()) {
            DatabaseMetaData md = conn.getMetaData();
            Map<String, ColumnInfo> columns = new LinkedHashMap<>();
            try (ResultSet rs = md.getColumns(null, schema, name, "%")) {
                while (rs.next()) {
                    columns.put(rs.getString("COLUMN_NAME"), new ColumnInfo(
                            rs.getString("COLUMN_NAME"),
                            rs.getString("TYPE_NAME") + "(" + rs.getInt("COLUMN_SIZE") + ")",
                            rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                            rs.getString("COLUMN_DEF"),
                            rs.getString("REMARKS"),
                            false));
                }
            }
            try (ResultSet rs = md.getPrimaryKeys(null, schema, name)) {
                while (rs.next()) {
                    String col = rs.getString("COLUMN_NAME");
                    ColumnInfo c = columns.get(col);
                    if (c != null) {
                        columns.put(col, new ColumnInfo(c.name(), c.type(), c.nullable(),
                                c.defaultValue(), c.remarks(), true));
                    }
                }
            }
            Map<String, IndexInfo> indexes = new LinkedHashMap<>();
            try (ResultSet rs = md.getIndexInfo(null, schema, name, false, false)) {
                while (rs.next()) {
                    String idxName = rs.getString("INDEX_NAME");
                    String col = rs.getString("COLUMN_NAME");
                    if (idxName == null || col == null) {
                        continue;
                    }
                    boolean unique = !rs.getBoolean("NON_UNIQUE");
                    indexes.computeIfAbsent(idxName, k -> new IndexInfo(k, unique, new ArrayList<>()))
                            .columns().add(col);
                }
            }
            return new DescribeResult(new ArrayList<>(columns.values()), new ArrayList<>(indexes.values()));
        }
    }
}
