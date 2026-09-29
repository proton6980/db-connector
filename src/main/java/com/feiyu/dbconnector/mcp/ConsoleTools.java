package com.feiyu.dbconnector.mcp;

import io.micronaut.mcp.annotations.Tool;
import jakarta.inject.Singleton;

@Singleton
public class ConsoleTools {

    @Tool(description = "获取 Web 管理控制台地址。控制台提供连接管理、查询执行、审计日志、仪表盘等功能。")
    public String open_console() {
        String portStr = System.getenv("DBCONNECTOR_PORT");
        int javaPort = 8080;
        if (portStr != null && !portStr.isBlank()) {
            try {
                javaPort = Integer.parseInt(portStr);
            } catch (NumberFormatException ignored) {
            }
        }
        String webPortStr = System.getenv("DBCONNECTOR_WEB_PORT");
        int webPort = 8081;
        if (webPortStr != null && !webPortStr.isBlank()) {
            try {
                webPort = Integer.parseInt(webPortStr);
            } catch (NumberFormatException ignored) {
            }
        }
        return "Web 管理控制台地址: http://127.0.0.1:" + webPort + "\n" +
               "MCP SSE Endpoint: http://127.0.0.1:" + javaPort + "/mcp\n" +
               "在浏览器中打开上述控制台地址即可管理数据库连接、执行查询和查看审计日志。";
    }
}