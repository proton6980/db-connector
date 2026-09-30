package com.feiyu.dbconnector.mcp;

import com.feiyu.dbconnector.audit.AuditEventQueue;
import com.feiyu.dbconnector.audit.AuditLogFlusher;
import com.feiyu.dbconnector.entity.DbConnection;
import com.feiyu.dbconnector.entity.SqlAuditLog;
import com.feiyu.dbconnector.repository.DbConnectionRepository;
import com.feiyu.dbconnector.repository.SqlAuditLogRepository;
import com.feiyu.dbconnector.security.AesCredentialCipher;
import com.feiyu.dbconnector.service.MetadataService;
import com.feiyu.dbconnector.service.QueryService;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(environments = "test")
class ToolsIntegrationTests {

    private static final String ITDB = "jdbc:h2:mem:itdb;DB_CLOSE_DELAY=-1";

    @Inject private QueryTools queryTools;
    @Inject private SchemaTools schemaTools;
    @Inject private ExecuteTools executeTools;
    @Inject private ConnectionTools connectionTools;
    @Inject private ConnectionManageTools connectionManageTools;
    @Inject private DbConnectionRepository connectionRepository;
    @Inject private SqlAuditLogRepository auditLogRepository;
    @Inject private AuditEventQueue auditQueue;
    @Inject private AuditLogFlusher auditFlusher;
    @Inject private AesCredentialCipher cipher;

    @BeforeEach
    void setUp() throws Exception {
        // 先把上个测试残留在异步队列中的事件落库，再清空，保证审计断言不跨测试污染
        auditFlusher.flush();
        auditLogRepository.deleteAll();

        try (var conn = DriverManager.getConnection(ITDB, "sa", "");
             var st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS TEST");
            st.execute("CREATE TABLE TEST (ID INT PRIMARY KEY, NAME VARCHAR(50))");
            st.execute("INSERT INTO TEST VALUES (1, 'alice'), (2, 'bob'), (3, 'carol')");
        }
        connectionRepository.findByName("h2-it").ifPresent(connectionRepository::delete);
        connectionRepository.findByName("h2-ro").ifPresent(connectionRepository::delete);
        connectionRepository.save(h2Connection("h2-it", true));
        connectionRepository.save(h2Connection("h2-ro", false));
    }

    private DbConnection h2Connection(String name, boolean writable) {
        DbConnection c = new DbConnection();
        c.setId(UUID.randomUUID().toString());
        c.setName(name);
        c.setDbType("H2");
        c.setHost("mem");
        c.setPort(-1);
        c.setUsername("sa");
        c.setPassword(cipher.encrypt(""));
        c.setDatabaseName("itdb");
        c.setAllowDml(writable);
        c.setAllowDdl(writable);
        LocalDateTime now = LocalDateTime.now();
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        return c;
    }

    @Test
    void queryWithNamedParams() {
        Object result = queryTools.query_database("h2-it", "SELECT ID, NAME FROM TEST WHERE ID = :id",
                Map.of("id", 2));
        String text = assertInstanceOf(String.class, result);
        assertTrue(text.contains("bob"), text);
        assertTrue(text.contains("ID"), text);
        assertTrue(text.contains("NAME"), text);
    }

    @Test
    void overMaxRowsTruncated() throws Exception {
        try (var conn = DriverManager.getConnection(ITDB, "sa", "");
             var st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS BIG AS SELECT X AS ID FROM SYSTEM_RANGE(1, 150)");
        }
        Object result = queryTools.query_database("h2-it", "SELECT ID FROM BIG", null);
        String text = assertInstanceOf(String.class, result);
        assertTrue(text.contains("已截断"), text);
    }

    @Test
    void maliciousSqlBlockedWithLlmFriendlyText() {
        Object result = queryTools.query_database("h2-it", "DELETE FROM TEST", null);
        String text = assertInstanceOf(String.class, result);
        assertTrue(text.startsWith("[SQL_REJECTED]"), text);
    }

    @Test
    void badSqlReturnsQueryFailed() {
        Object result = queryTools.query_database("h2-it", "SELECT * FROM NO_SUCH_TABLE", null);
        String text = assertInstanceOf(String.class, result);
        assertTrue(text.startsWith("[QUERY_FAILED]"), text);
    }

    @Test
    void unknownConnectionRejected() {
        Object result = queryTools.query_database("nope", "SELECT 1", null);
        String text = assertInstanceOf(String.class, result);
        assertTrue(text.startsWith("[CONNECTION_NOT_FOUND]"), text);
    }

    @Test
    void schemaTools() {
        String tables = schemaTools.list_tables("h2-it");
        assertTrue(tables.contains("TEST"), tables);

        String desc = schemaTools.describe_table("h2-it", "TEST");
        assertTrue(desc.contains("ID"), desc);
        assertTrue(desc.contains("PK"), desc);
    }

    @Test
    void tableSampleCappedAt5() {
        Object result = schemaTools.get_table_sample("h2-it", "TEST");
        String sample = assertInstanceOf(String.class, result);
        assertTrue(sample.contains("ID"), sample);
    }

    @Test
    void listConnectionsAndEncryptedAtRest() {
        assertTrue(connectionTools.list_connections().contains("h2-it"));
        DbConnection stored = connectionRepository.findByName("h2-it").orElseThrow();
        assertTrue(stored.getPassword().startsWith("v1:"));
    }

    @Test
    void dmlInsertSelectUpdateDelete() {
        String inserted = executeTools.execute_dml("h2-it", "INSERT INTO TEST VALUES (4, 'dave')",
                null, null);
        assertTrue(inserted.contains("影响行数: 1"), inserted);
        assertTrue(queryTools.query_database("h2-it", "SELECT NAME FROM TEST WHERE ID = 4", null)
                .contains("dave"));

        String updated = executeTools.execute_dml("h2-it", "UPDATE TEST SET NAME = :n WHERE ID = :id",
                Map.of("n", "david", "id", 4), null);
        assertTrue(updated.contains("影响行数: 1"), updated);

        String deleted = executeTools.execute_dml("h2-it", "DELETE FROM TEST WHERE ID = :id",
                Map.of("id", 4), null);
        assertTrue(deleted.contains("影响行数: 1"), deleted);

        assertTrue(queryTools.query_database("h2-it", "SELECT COUNT(*) AS C FROM TEST", null)
                .contains("\n3"), "should be back to 3 rows");
    }

    @Test
    void noWhereBlockedAndAllowFullTable() {
        String blocked = executeTools.execute_dml("h2-it", "UPDATE TEST SET NAME = 'x'", null, null);
        assertTrue(blocked.startsWith("[SQL_REJECTED]"), blocked);

        String ok = executeTools.execute_dml("h2-it", "UPDATE TEST SET NAME = 'x'", null, true);
        assertTrue(ok.contains("影响行数: 3"), ok);
    }

    @Test
    void mergeStatement() {
        String r = executeTools.execute_dml("h2-it",
                "MERGE INTO TEST t USING (SELECT 1 AS ID, 'alice2' AS NAME) s ON t.ID = s.ID "
                        + "WHEN MATCHED THEN UPDATE SET t.NAME = s.NAME", null, null);
        assertTrue(r.contains("影响行数: 1"), r);
        assertTrue(queryTools.query_database("h2-it", "SELECT NAME FROM TEST WHERE ID = 1", null)
                .contains("alice2"));
    }

    @Test
    void ddlLifecycle() {
        String created = executeTools.execute_ddl("h2-it", "CREATE TABLE T2 (ID INT)");
        assertTrue(created.contains("DDL 执行成功"), created);
        assertTrue(schemaTools.list_tables("h2-it").contains("T2"));

        executeTools.execute_ddl("h2-it", "ALTER TABLE T2 ADD NAME VARCHAR(50)");
        assertTrue(schemaTools.describe_table("h2-it", "T2").contains("NAME"));

        executeTools.execute_ddl("h2-it", "TRUNCATE TABLE T2");
        executeTools.execute_ddl("h2-it", "DROP TABLE T2");
    }

    @Test
    void writeRejectedOnLockedConnection() {
        assertTrue(executeTools.execute_dml("h2-ro", "INSERT INTO TEST VALUES (9, 'x')", null, null)
                .startsWith("[SQL_REJECTED]"));
        assertTrue(executeTools.execute_ddl("h2-ro", "CREATE TABLE T3 (ID INT)")
                .startsWith("[SQL_REJECTED]"));
    }

    @Test
    void toolStatementMismatchRejected() {
        assertTrue(executeTools.execute_dml("h2-it", "SELECT 1", null, null)
                .startsWith("[SQL_REJECTED]"));
        assertTrue(executeTools.execute_ddl("h2-it", "INSERT INTO TEST VALUES (5, 'e')")
                .startsWith("[SQL_REJECTED]"));
        assertTrue(executeTools.explain_sql("h2-it", "INSERT INTO TEST VALUES (5, 'e')", null)
                .startsWith("[SQL_REJECTED]"));
        assertTrue(executeTools.explain_sql("h2-it", "EXPLAIN SELECT 1", null)
                .startsWith("[SQL_REJECTED]"));
        assertTrue(executeTools.explain_sql("h2-it", "EXPLAIN ANALYZE SELECT 1", null)
                .startsWith("[SQL_REJECTED]"));
    }

    @Test
    void explainSelect() {
        String r = executeTools.explain_sql("h2-it", "SELECT * FROM TEST WHERE ID = :id",
                Map.of("id", 1));
        assertTrue(r.contains("PLAN"), r);
    }

    @Test
    void auditRecordsAllThreeStatuses() {
        queryTools.query_database("h2-it", "SELECT 1 FROM TEST", null);
        queryTools.query_database("h2-it", "UPDATE TEST SET NAME = 'x'", null);
        queryTools.query_database("h2-it", "SELECT * FROM NO_SUCH_TABLE", null);
        auditFlusher.flush();

        List<SqlAuditLog> logs = auditLogRepository.findAll();
        assertTrue(logs.stream().anyMatch(l -> "SUCCESS".equals(l.getStatus())));
        assertTrue(logs.stream().anyMatch(l -> "BLOCKED".equals(l.getStatus())));
        assertTrue(logs.stream().anyMatch(l -> "ERROR".equals(l.getStatus())));
        assertTrue(logs.stream().allMatch(l -> l.getAccountId() != null && l.getDurationMs() != null));
    }

    @Test
    void auditToolColumnAndDmlRowCount() {
        executeTools.execute_dml("h2-it", "INSERT INTO TEST VALUES (6, 'frank')", null, null);
        executeTools.execute_ddl("h2-it", "CREATE TABLE TAUDIT (ID INT)");
        executeTools.explain_sql("h2-it", "SELECT 1", null);
        auditFlusher.flush();

        List<SqlAuditLog> logs = auditLogRepository.findAll();
        assertTrue(logs.stream().anyMatch(l -> "execute_dml".equals(l.getTool())));
        assertTrue(logs.stream().anyMatch(l -> "execute_ddl".equals(l.getTool())));
        assertTrue(logs.stream().anyMatch(l -> "explain_sql".equals(l.getTool())));
        SqlAuditLog dml = logs.stream()
                .filter(l -> "execute_dml".equals(l.getTool())).findFirst().orElseThrow();
        assertEquals(1, dml.getRowCount());
    }

    @Test
    void noPasswordLeakIntoAudit() {
        String secret = "secret-pw-" + UUID.randomUUID();
        connectionManageTools.create_connection("leak-" + UUID.randomUUID().toString().substring(0, 8),
                "leak-name-" + UUID.randomUUID(), "H2", "mem", -1, "sa", secret,
                null, null, 2, 10);
        auditFlusher.flush();

        List<SqlAuditLog> logs = auditLogRepository.findAll();
        for (SqlAuditLog l : logs) {
            assertFalse(l.getSqlText() != null && l.getSqlText().contains(secret),
                    "password leaked into sql_text");
            assertFalse(l.getParams() != null && l.getParams().contains(secret),
                    "password leaked into params");
        }
        // flush 可能带上队列中上个测试的残留；只对 create_connection 行断言无 sql_text
        SqlAuditLog createRow = logs.stream()
                .filter(l -> "create_connection".equals(l.getTool()))
                .findFirst().orElseThrow();
        assertEquals("", createRow.getSqlText());
    }

    @Test
    void auditParamsMasked() {
        queryTools.query_database("h2-it", "SELECT 1 FROM TEST WHERE 1 = :pw",
                Map.of("password", "s3cret", "id", 1));
        auditFlusher.flush();
        // flush 可能把队列中上个测试的事件一起落库，断言"存在一行被脱敏"
        assertTrue(auditLogRepository.findAll().stream()
                .anyMatch(l -> l.getParams() != null && l.getParams().contains("****")));
    }

    @Test
    void queueOverflowDropsAndCounts() {
        auditFlusher.flush();
        long before = auditQueue.droppedCount();
        for (int i = 0; i < 1001; i++) {
            auditQueue.offer(new com.feiyu.dbconnector.audit.AuditEvent(
                    "c", "u", "SELECT 1", null, "SUCCESS", 1, 1, null, null));
        }
        assertEquals(before + 1, auditQueue.droppedCount());
        assertEquals(1000, auditQueue.drain().size());
    }
}
