package com.feiyu.dbconnector.dm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;

/**
 * Phase 1 T0 元数据探针：验证 DM DatabaseMetaData 的 getTables/getColumns/getPrimaryKeys/getIndexInfo
 * 及表注释 remarks 是否可取，决定 MetadataService 实现路径（拿不到 remarks 再走系统视图兜底）。
 * 仅在设置 DM_URL 时执行（前置：测试表见 {@code DmMetadataProbeTest#prepare}）。
 */
@EnabledIfEnvironmentVariable(named = "DM_URL", matches = ".+")
class DmMetadataProbeTest {

    @Test
    void probeMetadataApis() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                System.getenv("DM_URL"),
                System.getenv().getOrDefault("DM_USER", "SYSDBA"),
                System.getenv().getOrDefault("DM_PASSWORD", "SYSDBA001"))) {

            prepare(conn);

            DatabaseMetaData md = conn.getMetaData();
            System.out.println("== getTables ==");
            try (ResultSet rs = md.getTables(null, null, "P1_PROBE%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    System.out.printf("table=%s schema=%s remarks=[%s]%n",
                            rs.getString("TABLE_NAME"), rs.getString("TABLE_SCHEM"), rs.getString("REMARKS"));
                }
            }

            System.out.println("== getColumns ==");
            try (ResultSet rs = md.getColumns(null, null, "P1_PROBE_T", "%")) {
                while (rs.next()) {
                    System.out.printf("col=%s type=%s(%s) nullable=%s default=[%s] remarks=[%s]%n",
                            rs.getString("COLUMN_NAME"), rs.getString("TYPE_NAME"), rs.getInt("COLUMN_SIZE"),
                            rs.getInt("NULLABLE"), rs.getString("COLUMN_DEF"), rs.getString("REMARKS"));
                }
            }

            System.out.println("== getPrimaryKeys ==");
            try (ResultSet rs = md.getPrimaryKeys(null, null, "P1_PROBE_T")) {
                while (rs.next()) {
                    System.out.printf("pk=%s seq=%s name=%s%n",
                            rs.getString("COLUMN_NAME"), rs.getInt("KEY_SEQ"), rs.getString("PK_NAME"));
                }
            }

            System.out.println("== getIndexInfo ==");
            try (ResultSet rs = md.getIndexInfo(null, null, "P1_PROBE_T", false, false)) {
                while (rs.next()) {
                    System.out.printf("index=%s unique=%s col=%s%n",
                            rs.getString("INDEX_NAME"), !rs.getBoolean("NON_UNIQUE"), rs.getString("COLUMN_NAME"));
                }
            }
        }
    }

    /** 建带注释/主键/索引的探针表，幂等。 */
    private void prepare(Connection conn) throws Exception {
        try (var st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS P1_PROBE_T");
            st.execute("CREATE TABLE P1_PROBE_T (ID INT PRIMARY KEY, NAME VARCHAR(50), AGE INT)");
            st.execute("COMMENT ON TABLE P1_PROBE_T IS '探针表'");
            st.execute("COMMENT ON COLUMN P1_PROBE_T.NAME IS '姓名'");
            st.execute("CREATE INDEX IDX_P1_PROBE_AGE ON P1_PROBE_T(AGE)");
            st.execute("INSERT INTO P1_PROBE_T VALUES (1, 'a', 20)");
        }
    }
}
