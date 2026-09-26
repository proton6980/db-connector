package com.feiyu.dbconnector.repository;

import com.feiyu.dbconnector.entity.AccountPermission;
import com.feiyu.dbconnector.entity.DbConnection;
import com.feiyu.dbconnector.entity.SqlAuditLog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class PersistenceIntegrationTests {

    @Autowired
    private DbConnectionRepository connectionRepository;
    @Autowired
    private AccountPermissionRepository permissionRepository;
    @Autowired
    private SqlAuditLogRepository auditLogRepository;

    private DbConnection newConnection(String name) {
        DbConnection c = new DbConnection();
        c.setName(name);
        c.setDbType("MYSQL");
        c.setHost("127.0.0.1");
        c.setPort(3306);
        c.setUsername("root");
        c.setPassword("encrypted");
        return c;
    }

    @Test
    void crudAcrossAllTables() {
        DbConnection conn = connectionRepository.save(newConnection("test-conn"));
        assertNotNull(conn.getId());
        assertEquals(2, conn.getPoolMin()); // DDL/实体默认值
        assertTrue(conn.getActive());
        assertNotNull(conn.getCreatedAt());

        AccountPermission perm = new AccountPermission();
        perm.setAccountId("agent-1");
        perm.setConnectionId(conn.getId());
        perm.setPermission("READ_ONLY");
        perm.setAllowedTools("[\"query\",\"list_tables\"]");
        permissionRepository.save(perm);
        assertEquals(1, permissionRepository.findByAccountId("agent-1").size());

        SqlAuditLog log = new SqlAuditLog();
        log.setConnectionId(conn.getId());
        log.setAccountId("agent-1");
        log.setSqlText("SELECT 1");
        log.setStatus("SUCCESS");
        log.setRowCount(1);
        log.setDurationMs(3L);
        auditLogRepository.save(log);
        assertNotNull(log.getId()); // IDENTITY 自增
        assertNotNull(log.getExecutedAt());

        assertEquals(1, auditLogRepository.count());
    }

    @Test
    void foreignKeyIsEnforced() {
        AccountPermission perm = new AccountPermission();
        perm.setAccountId("agent-2");
        perm.setConnectionId("nonexistent-id");
        perm.setPermission("READ_ONLY");
        assertThrows(DataIntegrityViolationException.class, () -> permissionRepository.saveAndFlush(perm));
    }

    @Test
    void uniqueConnectionName() {
        connectionRepository.save(newConnection("dup-name"));
        assertDoesNotThrow(() -> connectionRepository.flush());
        assertThrows(DataIntegrityViolationException.class,
                () -> connectionRepository.saveAndFlush(newConnection("dup-name")));
    }
}
