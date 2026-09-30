package com.feiyu.dbconnector.security;

import com.feiyu.dbconnector.common.BizException;
import com.feiyu.dbconnector.common.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafetyGuardServiceTest {

    private final SafetyGuardService guard = new SafetyGuardService();

    private void rejected(String sql) {
        BizException e = assertThrows(BizException.class, () -> guard.check(sql));
        assertEquals(ErrorCode.SQL_REJECTED, e.getCode());
    }

    private SafetyGuardService.Kind kindOf(String sql) {
        return guard.classify(sql).kind();
    }

    @Test
    void allowsPlainSelect() {
        assertEquals("SELECT * FROM t", guard.check("select * from t"));
    }

    @Test
    void allowsWithCte() {
        assertDoesNotThrow(() -> guard.check("WITH c AS (SELECT 1 AS x) SELECT x FROM c"));
    }

    @Test
    void allowsUnionAndTrailingSemicolon() {
        assertDoesNotThrow(() -> guard.check("SELECT 1 UNION SELECT 2;"));
    }

    @Test
    void rejectsDmlAndDdlViaCheck() {
        rejected("UPDATE t SET a = 1");
        rejected("DELETE FROM t");
        rejected("INSERT INTO t VALUES (1)");
        rejected("DROP TABLE t");
        rejected("CREATE TABLE t (a INT)");
        rejected("ALTER TABLE t ADD b INT");
        rejected("TRUNCATE TABLE t");
        rejected("MERGE INTO t USING s ON (t.a = s.a) WHEN MATCHED THEN UPDATE SET t.b = s.b");
    }

    @Test
    void classifiesSelect() {
        assertEquals(SafetyGuardService.Kind.SELECT, kindOf("select * from t"));
        assertEquals(SafetyGuardService.Kind.SELECT, kindOf("SELECT 1 UNION SELECT 2"));
        assertEquals(SafetyGuardService.Kind.SELECT, kindOf("WITH c AS (SELECT 1) SELECT * FROM c"));
    }

    @Test
    void classifiesDml() {
        assertEquals(SafetyGuardService.Kind.DML, kindOf("INSERT INTO t VALUES (1)"));
        assertEquals(SafetyGuardService.Kind.DML, kindOf("UPDATE t SET a = 1 WHERE id = 1"));
        assertEquals(SafetyGuardService.Kind.DML, kindOf("DELETE FROM t WHERE id = 1"));
        assertEquals(SafetyGuardService.Kind.DML,
                kindOf("MERGE INTO t USING s ON (t.a = s.a) WHEN MATCHED THEN UPDATE SET t.b = s.b"));
        // H2 旧语法 MERGE ... KEY 不被 JSqlParser 支持，会被保守拒绝，不单独验证
    }

    @Test
    void classifiesDdl() {
        assertEquals(SafetyGuardService.Kind.DDL, kindOf("CREATE TABLE t (a INT)"));
        assertEquals(SafetyGuardService.Kind.DDL, kindOf("ALTER TABLE t ADD b INT"));
        assertEquals(SafetyGuardService.Kind.DDL, kindOf("DROP TABLE t"));
        assertEquals(SafetyGuardService.Kind.DDL, kindOf("TRUNCATE TABLE t"));
    }

    @Test
    void classifiesExplain() {
        assertEquals(SafetyGuardService.Kind.EXPLAIN, kindOf("EXPLAIN SELECT 1"));
        rejected("EXPLAIN ANALYZE SELECT 1");
        rejected("EXPLAIN t");
    }

    @Test
    void rejectsOtherParseableStatements() {
        rejected("COMMIT");
        rejected("GRANT SELECT ON t TO u");
        rejected("SHOW TABLES");
        rejected("CREATE INDEX idx ON t(a)");
        rejected("CREATE VIEW v AS SELECT 1");
    }

    @Test
    void rejectsMultiStatement() {
        rejected("SELECT 1; SELECT 2");
        rejected("SELECT 1; DELETE FROM t");
        rejected("SELECT 1 ;;");
    }

    @Test
    void rejectsSelectInto() {
        rejected("SELECT * INTO t2 FROM t1");
        rejected("EXPLAIN SELECT * INTO t2 FROM t1");
    }

    @Test
    void rejectsCommentSmuggling() {
        rejected("SELECT 1 /* comment */; DROP TABLE t");
        rejected("-- comment\nDROP TABLE t");
    }

    @Test
    void rejectsGarbageAndEmpty() {
        assertThrows(BizException.class, () -> guard.check(null));
        assertThrows(BizException.class, () -> guard.check("  "));
        assertThrows(BizException.class, () -> guard.check("not sql at all (((("));
        assertThrows(BizException.class, () -> guard.check("/* only comment */"));
    }

    @Test
    void normalizedOutput() {
        assertTrue(guard.check("select   1").toLowerCase().contains("select"));
    }

    @Test
    void bulkWriteGuard() {
        SafetyGuardService.ParsedStatement noWhereUpdate = guard.classify("UPDATE t SET a = 1");
        BizException e = assertThrows(BizException.class,
                () -> guard.assertBulkWriteGuarded(noWhereUpdate.statement(), false));
        assertEquals(ErrorCode.SQL_REJECTED, e.getCode());

        assertDoesNotThrow(() ->
                guard.assertBulkWriteGuarded(noWhereUpdate.statement(), true));

        SafetyGuardService.ParsedStatement withWhere = guard.classify("UPDATE t SET a = 1 WHERE id = 1");
        assertDoesNotThrow(() -> guard.assertBulkWriteGuarded(withWhere.statement(), false));

        SafetyGuardService.ParsedStatement deleteNoWhere = guard.classify("DELETE FROM t");
        assertThrows(BizException.class,
                () -> guard.assertBulkWriteGuarded(deleteNoWhere.statement(), false));

        SafetyGuardService.ParsedStatement insert = guard.classify("INSERT INTO t VALUES (1)");
        assertDoesNotThrow(() -> guard.assertBulkWriteGuarded(insert.statement(), false));
    }
}
