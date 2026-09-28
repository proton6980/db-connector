package com.feiyu.dbconnector.web.api;

import com.feiyu.dbconnector.entity.DbConnection;
import com.feiyu.dbconnector.entity.SqlAuditLog;
import com.feiyu.dbconnector.repository.DbConnectionRepository;
import com.feiyu.dbconnector.repository.SqlAuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuditLogApi.class)
class AuditLogApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SqlAuditLogRepository auditRepo;

    @MockitoBean
    private DbConnectionRepository connectionRepo;

    @Test
    void listReturnsPage() throws Exception {
        SqlAuditLog log = new SqlAuditLog();
        log.setId(1L);
        log.setConnectionId("conn1");
        log.setAccountId("alice");
        log.setSqlText("SELECT 1");
        log.setStatus("SUCCESS");
        log.setExecutedAt(LocalDateTime.now());
        when(auditRepo.search(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(log), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listWithFilters() throws Exception {
        when(auditRepo.search(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/logs")
                        .param("connectionId", "conn1")
                        .param("accountId", "alice")
                        .param("status", "ERROR")
                        .param("from", "2026-09-27T00:00:00")
                        .param("to", "2026-09-27T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void detailReturnsLog() throws Exception {
        SqlAuditLog log = new SqlAuditLog();
        log.setId(1L);
        log.setConnectionId("conn1");
        log.setAccountId("alice");
        log.setSqlText("SELECT 1");
        log.setStatus("SUCCESS");
        when(auditRepo.findById(1L)).thenReturn(Optional.of(log));

        mockMvc.perform(get("/api/logs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.sqlText").value("SELECT 1"));
    }

    @Test
    void detailMissingReturns404() throws Exception {
        when(auditRepo.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/logs/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("日志不存在: 99"));
    }

    @Test
    void filtersReturnsDropdownData() throws Exception {
        DbConnection c = new DbConnection();
        c.setId("conn1");
        c.setName("prod");
        when(connectionRepo.findAll()).thenReturn(List.of(c));
        when(auditRepo.distinctAccountIds()).thenReturn(List.of("alice", "bob"));

        mockMvc.perform(get("/api/logs/filters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connections[0].id").value("conn1"))
                .andExpect(jsonPath("$.connections[0].name").value("prod"))
                .andExpect(jsonPath("$.accountIds[0]").value("alice"));
    }

    @Test
    void exportReturnsCsv() throws Exception {
        when(auditRepo.search(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10000), 0));

        mockMvc.perform(get("/api/logs/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=audit_log.csv"));
    }

    @Test
    void csvEscapeHandlesSpecialCharacters() {
        assertEquals("\"a,\"\"b,c\"", AuditLogApi.csvEscape("a,\"b,c"));
        assertEquals("simple", AuditLogApi.csvEscape("simple"));
        assertEquals("", AuditLogApi.csvEscape(null));
    }

    private void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }
}
