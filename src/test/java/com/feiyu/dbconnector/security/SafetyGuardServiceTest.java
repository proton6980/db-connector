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
    void rejectsDmlAndDdl() {
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
    void rejectsMultiStatement() {
        rejected("SELECT 1; SELECT 2");
        rejected("SELECT 1; DELETE FROM t");
        rejected("SELECT 1 ;;");
    }

    @Test
    void rejectsSelectInto() {
        rejected("SELECT * INTO t2 FROM t1");
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
}
