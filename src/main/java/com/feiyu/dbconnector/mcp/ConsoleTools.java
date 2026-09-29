package com.feiyu.dbconnector.mcp;

import io.micronaut.mcp.annotations.Tool;
import jakarta.inject.Singleton;

@Singleton
public class ConsoleTools {

    @Tool(description = "获取 Web 管理控制台地址。控制台功能本期尚未提供，返回说明文案。")
    public String open_console() {
        return "Web 管理控制台本期尚未提供。连接管理请使用 create_connection / update_connection / delete_connection / test_connection 工具，查询请使用 query_database 工具。";
    }
}