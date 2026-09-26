package com.feiyu.dbconnector.config;

import com.feiyu.dbconnector.mcp.ProbeTools;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolConfig {

    @Bean
    MethodToolCallbackProvider toolCallbacks(ProbeTools probeTools) {
        return MethodToolCallbackProvider.builder().toolObjects(probeTools).build();
    }
}
