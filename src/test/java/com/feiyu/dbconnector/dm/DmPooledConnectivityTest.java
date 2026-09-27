package com.feiyu.dbconnector.dm;

import com.feiyu.dbconnector.datasource.DataSourceSpec;
import com.feiyu.dbconnector.datasource.DynamicDataSourceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 达梦经 Hikari 连接池的连通验证。仅在设置 DM_URL 环境变量时执行：
 * DM_URL=jdbc:dm://host:5236 DM_USER=SYSDBA DM_PASSWORD=SYSDBA \
 *   mvn test -Dtest=DmPooledConnectivityTest
 */
@EnabledIfEnvironmentVariable(named = "DM_URL", matches = ".+")
class DmPooledConnectivityTest {

    @Test
    void selectOneFromDualViaPool() throws Exception {
        DynamicDataSourceManager manager = new DynamicDataSourceManager();
        DataSourceSpec spec = new DataSourceSpec(
                System.getenv("DM_URL"),
                System.getenv().getOrDefault("DM_USER", "SYSDBA"),
                System.getenv().getOrDefault("DM_PASSWORD", "SYSDBA"),
                null, 1, 2, false); // connectionTestQuery=null：验证 DM 驱动 JDBC4 isValid 是否够用

        try (Connection conn = manager.getOrCreate("dm-spike", spec).getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1 FROM DUAL")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        } finally {
            manager.close("dm-spike");
        }
    }
}
