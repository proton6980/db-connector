package com.feiyu.dbconnector.config;

import com.feiyu.dbconnector.mcp.ConnectionTools;
import com.feiyu.dbconnector.mcp.ConsoleTools;
import com.feiyu.dbconnector.mcp.QueryTools;
import com.feiyu.dbconnector.mcp.SchemaTools;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolConfig {

    @Bean
    MethodToolCallbackProvider toolCallbacks(QueryTools queryTools, SchemaTools schemaTools,
                                             ConnectionTools connectionTools, ConsoleTools consoleTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(queryTools, schemaTools, connectionTools, consoleTools)
                .build();
    }
}