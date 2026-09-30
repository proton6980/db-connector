package com.feiyu.dbconnector.security;

import com.feiyu.dbconnector.common.BizException;
import com.feiyu.dbconnector.common.ErrorCode;
import jakarta.inject.Singleton;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.ExplainStatement;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.merge.Merge;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.truncate.Truncate;
import net.sf.jsqlparser.statement.update.Update;

@Singleton
public class SafetyGuardService {

    public enum Kind { SELECT, DML, DDL, EXPLAIN }

    public record ParsedStatement(Statement statement, Kind kind, String normalized) {}

    /**
     * 解析 + 分类，只做语法/结构/策略校验，不做连接级权限判断。
     * 多语句、不可解析、不支持的语句类型一律 SQL_REJECTED。
     */
    public ParsedStatement classify(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "sql 不能为空");
        }
        Statements statements;
        try {
            statements = CCJSqlParserUtil.parseStatements(sql);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SQL_REJECTED, "无法解析的 SQL: " + e.getMessage());
        }
        if (statements.size() != 1) {
            throw new BizException(ErrorCode.SQL_REJECTED,
                    statements.size() > 1 ? "拒绝多语句执行，仅允许单条 SQL"
                            : "空语句或仅含注释，仅允许单条 SQL");
        }
        Statement s0 = statements.getStatements().get(0);
        Kind kind = classify(s0);
        return new ParsedStatement(s0, kind, s0.toString());
    }

    private Kind classify(Statement s0) {
        if (s0 instanceof Select select) {
            if (select instanceof PlainSelect plain
                    && plain.getIntoTables() != null && !plain.getIntoTables().isEmpty()) {
                throw new BizException(ErrorCode.SQL_REJECTED, "拒绝 SELECT ... INTO");
            }
            return Kind.SELECT;
        }
        if (s0 instanceof ExplainStatement explain) {
            if (explain.getOption(ExplainStatement.OptionType.ANALYZE) != null) {
                throw new BizException(ErrorCode.SQL_REJECTED, "禁止 EXPLAIN ANALYZE（会真实执行语句）");
            }
            if (!(explain.getStatement() instanceof PlainSelect inner)) {
                throw new BizException(ErrorCode.SQL_REJECTED, "EXPLAIN 仅支持单条 SELECT");
            }
            if (inner.getIntoTables() != null && !inner.getIntoTables().isEmpty()) {
                throw new BizException(ErrorCode.SQL_REJECTED, "拒绝 SELECT ... INTO");
            }
            return Kind.EXPLAIN;
        }
        if (s0 instanceof Insert || s0 instanceof Update || s0 instanceof Delete || s0 instanceof Merge) {
            return Kind.DML;
        }
        if (s0 instanceof CreateTable || s0 instanceof Alter || s0 instanceof Drop || s0 instanceof Truncate) {
            return Kind.DDL;
        }
        throw new BizException(ErrorCode.SQL_REJECTED, "不支持的语句类型: " + s0.getClass().getSimpleName());
    }

    /**
     * SELECT-only 门面：供只读查询路径使用，行为与放开前等价。
     */
    public String check(String sql) {
        ParsedStatement p = classify(sql);
        if (p.kind() != Kind.SELECT) {
            throw new BizException(ErrorCode.SQL_REJECTED, "仅允许单条 SELECT 查询");
        }
        return p.normalized();
    }

    /**
     * UPDATE/DELETE 必须带 WHERE；确需全表操作时由调用方显式 allowFullTable=true。
     */
    public void assertBulkWriteGuarded(Statement stmt, boolean allowFullTable) {
        boolean noWhere = (stmt instanceof Update u && u.getWhere() == null)
                || (stmt instanceof Delete d && d.getWhere() == null);
        if (noWhere && !allowFullTable) {
            throw new BizException(ErrorCode.SQL_REJECTED,
                    "UPDATE/DELETE 缺少 WHERE；确需全表操作必须显式 allowFullTable=true");
        }
    }
}
