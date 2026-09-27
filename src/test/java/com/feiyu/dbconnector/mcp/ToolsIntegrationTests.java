package com.feiyu.dbconnector.mcp;

import com.feiyu.dbconnector.audit.AuditEventQueue;
import com.feiyu.dbconnector.audit.AuditLogFlusher;
import com.feiyu.dbconnector.entity.DbConnection;
import com.feiyu.dbconnector.entity.SqlAuditLog;
import com.feiyu.dbconnector.repository.DbConnectionRepository;
import com.feiyu.dbconnector.repository.SqlAuditLogRepository;
import com.feiyu.dbconnector.service.MetadataService;
import com.feiyu.dbconnector.service.QueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.DriverManager;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H2 全链路集成测试：tool → 守卫 → 只读池 → 结果结构 → 审计落库。
 * 目标库用 jdbc:h2:mem:itdb（普通连接造数，只读池查询，顺带验证 readOnly 防御）。
 */
@SpringBootTest
class ToolsIntegrationTests {

    private static final String ITDB = "jdbc:h2:mem:itdb;DB_CLOSE_DELAY=-1";

    @Autowired private QueryTools queryTools;
    @Autowired private SchemaTools schemaTools;
    @Autowired private ConnectionTools connectionTools;
    @Autowired private DbConnectionRepository connectionRepository;
    @Autowired private SqlAuditLogRepository auditLogRepository;
    @Autowired private AuditEventQueue auditQueue;
    @Autowired private AuditLogFlusher auditFlusher;

    @BeforeEach
    void setUp() throws Exception {
        try (var conn = DriverManager.getConnection(ITDB, "sa", "");
             var st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS TEST");
            st.execute("CREATE TABLE TEST (ID INT PRIMARY KEY, NAME VARCHAR(50))");
            st.execute("INSERT INTO TEST VALUES (1, 'alice'), (2, 'bob'), (3, 'carol')");
        }
        DbConnection c = new DbConnection();
        c.setName("h2-it");
        c.setDbType("H2");
        c.setHost("mem");
        c.setPort(-1);
        c.setUsername("sa");
        c.setPassword("");
        c.setDatabaseName("itdb");
        connectionRepository.findByName("h2-it").ifPresent(connectionRepository::delete);
        connectionRepository.save(c);
        auditLogRepository.deleteAll();
    }

    @Test
    void queryWithNamedParams() {
        Object result = queryTools.query_database("h2-it", "SELECT ID, NAME FROM TEST WHERE ID = :id",
                Map.of("id", 2));
        QueryService.QueryResult q = assertInstanceOf(QueryService.QueryResult.class, result);
        assertEquals(1, q.rowCount());
        assertEquals("bob", q.rows().get(0).get("NAME"));
        assertEquals(List.of("ID", "NAME"), q.columns().stream().map(QueryService.Column::name).toList());
        assertEquals(false, q.truncated());
    }

    @Test
    void overMaxRowsTruncated() throws Exception {
        try (var conn = DriverManager.getConnection(ITDB, "sa", "");
             var st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS BIG AS SELECT X AS ID FROM SYSTEM_RANGE(1, 150)");
        }
        Object result = queryTools.query_database("h2-it", "SELECT ID FROM BIG", null);
        QueryService.QueryResult q = assertInstanceOf(QueryService.QueryResult.class, result);
        assertEquals(100, q.rowCount());
        assertTrue(q.truncated());
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
        @SuppressWarnings("unchecked")
        List<MetadataService.TableInfo> tables = (List<MetadataService.TableInfo>) schemaTools.list_tables("h2-it");
        assertTrue(tables.stream().anyMatch(t -> t.name().equals("TEST")));

        MetadataService.DescribeResult d = assertInstanceOf(MetadataService.DescribeResult.class,
                schemaTools.describe_table("h2-it", "TEST"));
        MetadataService.ColumnInfo id = d.columns().stream()
                .filter(c -> c.name().equals("ID")).findFirst().orElseThrow();
        assertTrue(id.primaryKey());
        assertTrue(d.indexes().stream().anyMatch(i -> i.unique()));
    }

    @Test
    void tableSampleCappedAt5() {
        Object result = schemaTools.get_table_sample("h2-it", "TEST");
        QueryService.QueryResult q = assertInstanceOf(QueryService.QueryResult.class, result);
        assertEquals(3, q.rowCount());
    }

    @Test
    void listConnectionsAndEncryptedAtRest() {
        assertTrue(connectionTools.list_connections().stream().anyMatch(c -> "h2-it".equals(c.name())));
        // 落库为密文（converter 加密），实体读出为明文（converter 解密）
        DbConnection stored = connectionRepository.findByName("h2-it").orElseThrow();
        assertEquals("", stored.getPassword());
        assertTrue(connectionRepository.findRawPasswordByName("h2-it").startsWith("v1:"));
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
    void auditParamsMasked() {
        queryTools.query_database("h2-it", "SELECT 1 FROM TEST WHERE 1 = :pw",
                Map.of("password", "s3cret", "id", 1));
        auditFlusher.flush();
        String params = auditLogRepository.findAll().stream()
                .filter(l -> l.getParams() != null).findFirst().orElseThrow().getParams();
        assertTrue(params.contains("****"), params);
    }

    @Test
    void queueOverflowDropsAndCounts() {
        auditFlusher.flush(); // 清空遗留事件，保证本测试计数确定
        long before = auditQueue.droppedCount();
        for (int i = 0; i < 1001; i++) {
            auditQueue.offer(new com.feiyu.dbconnector.audit.AuditEvent(
                    "c", "u", "SELECT 1", null, "SUCCESS", 1, 1, null));
        }
        assertEquals(before + 1, auditQueue.droppedCount());
        assertEquals(1000, auditQueue.drain().size());
    }
}
