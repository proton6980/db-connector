package com.feiyu.dbconnector.dm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 达梦写路径冒烟：DML 增改删 + DDL 建/删表 + EXPLAIN。
 * 仅在 DM 容器/实例在场时运行（DM_URL 设置），H2 测试覆盖不到 DM 的行计数与计划输出形状。
 */
@EnabledIfEnvironmentVariable(named = "DM_URL", matches = ".+")
class DmWritePathTest {

    @Test
    void dmlDdlExplain() throws Exception {
        String url = System.getenv("DM_URL");
        String user = System.getenv().getOrDefault("DM_USER", "SYSDBA");
        String password = System.getenv().getOrDefault("DM_PASSWORD", "SYSDBA");

        String table = "DM_WRITE_" + System.nanoTime();
        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement st = conn.createStatement()) {

            st.execute("CREATE TABLE " + table + " (ID INT PRIMARY KEY, NAME VARCHAR(50))");
            try {
                assertEquals(1, st.executeUpdate("INSERT INTO " + table + " VALUES (1, 'alice')"));

                try (ResultSet rs = st.executeQuery("SELECT NAME FROM " + table + " WHERE ID = 1")) {
                    assertTrue(rs.next());
                    assertEquals("alice", rs.getString(1));
                }

                assertEquals(1, st.executeUpdate("UPDATE " + table + " SET NAME = 'bob' WHERE ID = 1"));
                assertEquals(1, st.executeUpdate("DELETE FROM " + table + " WHERE ID = 1"));

                // DM 的计划不在标准结果集，执行 EXPLAIN 后通过 Statement 专有 API 取文本
                st.execute("EXPLAIN SELECT * FROM " + table);
                String plan = ((dm.jdbc.driver.DmdbStatement) st).getExplain();
                assertTrue(plan != null && !plan.isBlank(), "EXPLAIN 应产生计划文本");
            } finally {
                st.execute("DROP TABLE " + table);
            }
        }
    }
}
