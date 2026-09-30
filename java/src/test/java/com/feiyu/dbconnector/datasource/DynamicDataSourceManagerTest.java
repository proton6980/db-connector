package com.feiyu.dbconnector.datasource;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 纯 JUnit，不启动 Spring 上下文，用 H2 验证池的创建/复用/关闭。
 */
class DynamicDataSourceManagerTest {

    @Test
    void createReuseClose() throws Exception {
        DynamicDataSourceManager manager = new DynamicDataSourceManager();
        DataSourceSpec spec = new DataSourceSpec("jdbc:h2:mem:p0spike", "sa", "", null, 1, 2, false);

        HikariDataSource ds = manager.getOrCreate("h2", spec);
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }

        assertSame(ds, manager.getOrCreate("h2", spec)); // 同 key 复用同一池

        manager.close("h2");
        assertTrue(ds.isClosed());
    }
}
