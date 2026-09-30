package com.feiyu.dbconnector.dm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 达梦数据库连通测试。仅在设置 DM_URL 环境变量时执行：
 * DM_URL=jdbc:dm://host:5236 DM_USER=SYSDBA DM_PASSWORD=SYSDBA \
 *   mvn test -Dtest=DmConnectivityTest
 */
@EnabledIfEnvironmentVariable(named = "DM_URL", matches = ".+")
class DmConnectivityTest {

    @Test
    void selectOneFromDual() throws Exception {
        String url = System.getenv("DM_URL");
        String user = System.getenv().getOrDefault("DM_USER", "SYSDBA");
        String password = System.getenv().getOrDefault("DM_PASSWORD", "SYSDBA");

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1 FROM DUAL")) {
            assertEquals(true, rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }
}
