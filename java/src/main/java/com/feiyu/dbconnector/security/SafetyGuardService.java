package com.feiyu.dbconnector.security;

import com.feiyu.dbconnector.common.BizException;
import com.feiyu.dbconnector.common.ErrorCode;
import jakarta.inject.Singleton;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;

@Singleton
public class SafetyGuardService {

    public String check(String sql) {
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
                    statements.size() > 1 ? "拒绝多语句执行，仅允许单条查询"
                            : "空语句或仅含注释，仅允许单条 SELECT 查询");
        }
        if (!(statements.getStatements().get(0) instanceof Select select)) {
            throw new BizException(ErrorCode.SQL_REJECTED, "仅允许单条 SELECT 查询");
        }
        if (select instanceof PlainSelect plain && plain.getIntoTables() != null && !plain.getIntoTables().isEmpty()) {
            throw new BizException(ErrorCode.SQL_REJECTED, "拒绝 SELECT ... INTO");
        }
        return select.toString();
    }
}