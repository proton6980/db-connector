package com.feiyu.dbconnector.mcp;

import com.feiyu.dbconnector.datasource.DataSourceSpec;
import com.feiyu.dbconnector.datasource.DynamicDataSourceManager;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * ponytail: Phase 0 一次性探针，凭证取环境变量；Phase 1 正式 Tool 实现后整体删除。
 */
@Component
public class ProbeTools {

    private final DynamicDataSourceManager dataSourceManager;

    public ProbeTools(DynamicDataSourceManager dataSourceManager) {
        this.dataSourceManager = dataSourceManager;
    }

    @Tool(description = "存活探针，返回 pong")
    public String ping() {
        return "pong";
    }

    @Tool(description = "达梦连通探针：执行 SELECT 1 FROM DUAL 并返回结果")
    public String dmProbe() {
        String url = System.getenv("DM_URL");
        if (url == null || url.isBlank()) {
            return "未配置 DM_URL 环境变量，无法探测达梦连接";
        }
        String user = System.getenv().getOrDefault("DM_USER", "SYSDBA");
        String password = System.getenv().getOrDefault("DM_PASSWORD", "SYSDBA");
        DataSourceSpec spec = new DataSourceSpec(url, user, password, "SELECT 1 FROM DUAL", 1, 2);
        try (Connection conn = dataSourceManager.getOrCreate("dm-spike", spec).getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1 FROM DUAL")) {
            rs.next();
            return "dm_probe=" + rs.getInt(1);
        } catch (Exception e) {
            return "达梦探测失败: " + e.getMessage();
        }
    }
}
