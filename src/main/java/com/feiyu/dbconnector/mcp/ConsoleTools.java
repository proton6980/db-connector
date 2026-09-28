package com.feiyu.dbconnector.mcp;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConsoleTools {

    private final String baseUrl;

    public ConsoleTools(@Value("${server.port:8080}") int port,
                        @Value("${server.address:127.0.0.1}") String address) {
        this.baseUrl = "http://" + address + ":" + port;
    }

    @Tool(description = "获取 Web 管理控制台地址。返回仪表盘、连接管理、审计日志等页面的 URL，" +
            "开发者可在浏览器中打开查看。当用户需要可视化查看审计日志、管理数据库连接、查看仪表盘时调用。")
    public String open_console() {
        return """
                Web 管理控制台已就绪，请在浏览器中打开：

                仪表盘（QPS/错误率/Top SQL/连接池状态）：%s/dashboard
                数据库连接管理（新增/编辑/测试/删除）：  %s/connections
                SQL 审计日志（筛选/分页/详情/CSV 导出）：%s/logs

                提示：如未设置登录口令，直接访问即可；如已设置，需输入口令后进入。
                """.formatted(baseUrl, baseUrl, baseUrl).strip();
    }
}