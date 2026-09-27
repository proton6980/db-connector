package com.feiyu.dbconnector.security;

import com.feiyu.dbconnector.common.BizException;
import com.feiyu.dbconnector.common.ErrorCode;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.springframework.stereotype.Service;

/**
 * SQL 安全守卫：JSqlParser 语法级预检，只放行单条 SELECT / WITH(CTE) 开头的只读查询。
 * 拒绝 DDL/DML/多语句/SELECT INTO；注释夹带、大小写混淆天然绕不过解析器。
 * 多层防御的第一层，后续还有 JDBC readOnly/maxRows/timeout 与只读 DB 账号。
 */
@Service
public class SafetyGuardService {

    /** 校验通过返回归一化后的语句，否则抛 SQL_REJECTED。 */
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
        // INTO 只可能出现在 PlainSelect（含括号子查询），SetOperationList 语法上不允许
        if (select instanceof PlainSelect plain && plain.getIntoTables() != null && !plain.getIntoTables().isEmpty()) {
            throw new BizException(ErrorCode.SQL_REJECTED, "拒绝 SELECT ... INTO");
        }
        return select.toString();
    }
}
