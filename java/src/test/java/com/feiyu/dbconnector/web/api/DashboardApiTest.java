package com.feiyu.dbconnector.web.api;

import com.feiyu.dbconnector.datasource.DynamicDataSourceManager;
import com.feiyu.dbconnector.repository.DbConnectionRepository;
import com.feiyu.dbconnector.repository.SqlAuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardApi.class)
class DashboardApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SqlAuditLogRepository auditRepo;

    @MockitoBean
    private DbConnectionRepository connectionRepo;

    @MockitoBean
    private DynamicDataSourceManager dataSourceManager;

    @Test
    void statsReturnsNumericRates() throws Exception {
        when(auditRepo.countSince(any())).thenReturn(100L);
        List<Object[]> statusStats = new ArrayList<>();
        statusStats.add(new Object[]{"SUCCESS", 80L, 15.0});
        statusStats.add(new Object[]{"ERROR", 15L, 200.0});
        statusStats.add(new Object[]{"BLOCKED", 5L, 0.0});
        when(auditRepo.statusStatsSince(any())).thenReturn(statusStats);
        List<Object[]> topSql = new ArrayList<>();
        topSql.add(new Object[]{"SELECT 1", 50L, 10.0});
        when(auditRepo.topSql(anyInt())).thenReturn(topSql);
        when(auditRepo.findSince(any())).thenReturn(List.of());
        when(connectionRepo.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/dashboard/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLast24h").value(100))
                .andExpect(jsonPath("$.successCount").value(80))
                .andExpect(jsonPath("$.errorCount").value(15))
                .andExpect(jsonPath("$.blockedCount").value(5))
                .andExpect(jsonPath("$.errorRate").isNumber())
                .andExpect(jsonPath("$.avgDuration").isNumber())
                .andExpect(jsonPath("$.topSql[0].sqlText").value("SELECT 1"))
                .andExpect(jsonPath("$.topSql[0].count").value(50))
                .andExpect(jsonPath("$.poolStatus").isArray())
                .andExpect(jsonPath("$.hourlyQps").isMap());
    }

    @Test
    void statsWithNoData() throws Exception {
        when(auditRepo.countSince(any())).thenReturn(0L);
        when(auditRepo.statusStatsSince(any())).thenReturn(List.of());
        when(auditRepo.topSql(anyInt())).thenReturn(List.of());
        when(auditRepo.findSince(any())).thenReturn(List.of());
        when(connectionRepo.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/dashboard/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLast24h").value(0))
                .andExpect(jsonPath("$.errorRate").value(0.0))
                .andExpect(jsonPath("$.avgDuration").value(0.0));
    }
}
