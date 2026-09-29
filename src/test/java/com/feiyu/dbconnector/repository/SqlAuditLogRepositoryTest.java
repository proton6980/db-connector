package com.feiyu.dbconnector.repository;

import com.feiyu.dbconnector.entity.SqlAuditLog;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest(environments = "test")
class SqlAuditLogRepositoryTest {

    @Inject
    private SqlAuditLogRepository repo;

    @BeforeEach
    void cleanUp() {
        repo.deleteAll();
    }

    private SqlAuditLog createLog(String connId, String accountId, String status, long duration) {
        SqlAuditLog log = new SqlAuditLog();
        log.setConnectionId(connId);
        log.setAccountId(accountId);
        log.setSqlText("SELECT 1");
        log.setStatus(status);
        log.setDurationMs(duration);
        log.setExecutedAt(LocalDateTime.now());
        return repo.save(log);
    }

    @Test
    void searchByConnectionId() {
        createLog("conn1", "user1", "SUCCESS", 10);
        createLog("conn2", "user1", "SUCCESS", 20);

        Page<SqlAuditLog> result = repo.search("conn1", null, null, null, null, Pageable.from(0, 20));
        assertEquals(1, result.getTotalSize());
    }

    @Test
    void searchByStatus() {
        createLog("conn1", "user1", "SUCCESS", 10);
        createLog("conn1", "user1", "ERROR", 20);

        Page<SqlAuditLog> result = repo.search(null, null, "ERROR", null, null, Pageable.from(0, 20));
        assertEquals(1, result.getTotalSize());
    }

    @Test
    void searchAllReturnsEverything() {
        createLog("conn1", "user1", "SUCCESS", 10);
        createLog("conn2", "user2", "ERROR", 20);

        Page<SqlAuditLog> result = repo.search(null, null, null, null, null, Pageable.from(0, 20));
        assertEquals(2, result.getTotalSize());
    }

    @Test
    void distinctAccountIds() {
        createLog("conn1", "alice", "SUCCESS", 10);
        createLog("conn1", "bob", "SUCCESS", 20);

        List<String> ids = repo.distinctAccountIds();
        assertTrue(ids.contains("alice"));
        assertTrue(ids.contains("bob"));
    }

    @Test
    void statusStatsSince() {
        createLog("conn1", "user1", "SUCCESS", 10);
        createLog("conn1", "user1", "ERROR", 50);

        List<Object[]> stats = repo.statusStatsSince(LocalDateTime.now().minusHours(1));
        assertEquals(2, stats.size());
    }

    @Test
    void topSqlReturnsResults() {
        createLog("conn1", "user1", "SUCCESS", 10);
        createLog("conn1", "user1", "SUCCESS", 20);

        List<Object[]> top = repo.topSql(5);
        assertFalse(top.isEmpty());
    }

    @Test
    void countSince() {
        createLog("conn1", "user1", "SUCCESS", 10);
        long count = repo.countSince(LocalDateTime.now().minusHours(1));
        assertEquals(1, count);
    }
}