package com.feiyu.dbconnector.oracle;

import com.feiyu.dbconnector.entity.DbConnection;
import com.feiyu.dbconnector.mcp.ExecuteTools;
import com.feiyu.dbconnector.mcp.QueryTools;
import com.feiyu.dbconnector.mcp.SchemaTools;
import com.feiyu.dbconnector.repository.DbConnectionRepository;
import com.feiyu.dbconnector.security.AesCredentialCipher;
import com.feiyu.dbconnector.service.ConnectionService;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 完整应用链路（Execute/Query/Schema Tools → QueryService → Hikari → Oracle）冒烟，
 * 验证“用户即 schema”元数据语义与 EXPLAIN PLAN FOR + DBMS_XPLAN.DISPLAY 两步路径，H2 测试覆盖不到。
 * 仅 ORACLE_URL 设置时运行，如：
 * ORACLE_URL='jdbc:oracle:thin:@//localhost:1521/XEPDB1'
 * ORACLE_ADMIN_USER=system ORACLE_ADMIN_PASSWORD=***
 */
@MicronautTest(environments = "test")
@EnabledIfEnvironmentVariable(named = "ORACLE_URL", matches = ".+")
class OracleToolsSmokeTest {

    // Oracle URL 无法用 URI 解析（双冒号、@//），正则取 host/port/service
    private static final Pattern URL_PATTERN =
            Pattern.compile("jdbc:oracle:thin:@(?://)?([^:/?]+):(\\d+)/([^?]+)");

    @Inject private ExecuteTools executeTools;
    @Inject private QueryTools queryTools;
    @Inject private SchemaTools schemaTools;
    @Inject private DbConnectionRepository connectionRepository;
    @Inject private ConnectionService connectionService;
    @Inject private AesCredentialCipher cipher;

    @Test
    void fullToolChain() throws Exception {
        String rawUrl = System.getenv("ORACLE_URL");
        Matcher m = URL_PATTERN.matcher(rawUrl);
        assertTrue(m.matches(),
                "ORACLE_URL 需为 service name 形式: jdbc:oracle:thin:@//host:1521/service");
        String host = m.group(1);
        int port = Integer.parseInt(m.group(2));
        String service = m.group(3);
        String adminUser = System.getenv().getOrDefault("ORACLE_ADMIN_USER", "system");
        String adminPassword = System.getenv().getOrDefault("ORACLE_ADMIN_PASSWORD", "");

        // 临时用户即临时 schema（Oracle 无法轻量 CREATE DATABASE）
        String schema = "MCP_SMOKE_" + Long.toHexString(System.nanoTime()).toUpperCase();
        String password = "McpSmoke2026";
        String connId = "oracle-smoke-" + UUID.randomUUID().toString().substring(0, 8);
        String table = "ORACLE_SMOKE_" + Long.toHexString(System.nanoTime()).toUpperCase();

        try (java.sql.Connection admin = DriverManager.getConnection(rawUrl, adminUser, adminPassword);
             Statement st = admin.createStatement()) {
            st.execute("CREATE USER " + schema + " IDENTIFIED BY \"" + password + "\"");
            st.execute("GRANT CREATE SESSION, CREATE TABLE TO " + schema);
            st.execute("ALTER USER " + schema + " QUOTA UNLIMITED ON USERS");
        }

        DbConnection c = new DbConnection();
        c.setId(connId);
        c.setName(connId);
        c.setDbType("ORACLE");
        c.setHost(host);
        c.setPort(port);
        c.setUsername(schema);
        c.setPassword(cipher.encrypt(password));
        c.setDatabaseName(service);
        c.setAllowDml(true);
        c.setAllowDdl(true);
        LocalDateTime now = LocalDateTime.now();
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        connectionRepository.save(c);

        try {
            String created = executeTools.execute_ddl(connId,
                    "CREATE TABLE " + table + " (ID INT PRIMARY KEY, NAME VARCHAR(50))");
            assertTrue(created.contains("DDL 执行成功"), created);

            String tables = schemaTools.list_tables(connId);
            assertTrue(tables.contains(table), tables);

            // Oracle 前缀是 schema（即临时用户名）
            String describe = schemaTools.describe_table(connId, schema + "." + table);
            assertTrue(describe.contains("ID"), describe);
            assertTrue(describe.contains("NAME"), describe);

            String inserted = executeTools.execute_dml(connId,
                    "INSERT INTO " + table + " VALUES (1, 'alice')", null, null);
            assertTrue(inserted.contains("影响行数: 1"), inserted);

            String updated = executeTools.execute_dml(connId,
                    "UPDATE " + table + " SET NAME = :n WHERE ID = :id",
                    Map.of("n", "bob", "id", 1), null);
            assertTrue(updated.contains("影响行数: 1"), updated);

            String queried = queryTools.query_database(connId,
                    "SELECT NAME FROM " + table + " WHERE ID = :id", Map.of("id", 1));
            assertTrue(queried.contains("bob"), queried);

            // 走 EXPLAIN PLAN FOR + DBMS_XPLAN.DISPLAY 两步路径，计划文本含表名
            String plan = executeTools.explain_sql(connId,
                    "SELECT * FROM " + table + " WHERE ID = :id", Map.of("id", 1));
            assertTrue(plan.contains(table), plan);

            String deleted = executeTools.execute_dml(connId,
                    "DELETE FROM " + table + " WHERE ID = 1", null, null);
            assertTrue(deleted.contains("影响行数: 1"), deleted);

            String dropped = executeTools.execute_ddl(connId, "DROP TABLE " + table);
            assertTrue(dropped.contains("DDL 执行成功"), dropped);
        } finally {
            // 必须先关池（含该用户的活跃会话），否则 DROP USER 报 ORA-01940
            connectionService.delete(connId);
            try (java.sql.Connection admin = DriverManager.getConnection(rawUrl, adminUser, adminPassword);
                 Statement st = admin.createStatement()) {
                st.execute("DROP USER " + schema + " CASCADE");
            }
        }
    }
}
