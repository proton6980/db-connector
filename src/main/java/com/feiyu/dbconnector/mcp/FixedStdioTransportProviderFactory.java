package com.feiyu.dbconnector.mcp;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpServerSession;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;

@Factory
public class FixedStdioTransportProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(FixedStdioTransportProviderFactory.class);

    @Primary
    @Singleton
    public McpServerTransportProvider createStdioServerTransportProvider(McpJsonMapper jsonMapper) {
        return new FixedStdioTransportProvider(jsonMapper);
    }

    private static class FixedStdioTransportProvider implements McpServerTransportProvider {

        private static final List<String> PROTOCOL_VERSIONS = List.of(
                "2024-11-05",
                "2025-03-26",
                "2025-06-18",
                "2025-11-25"
        );

        private final StdioServerTransportProvider delegate;

        FixedStdioTransportProvider(McpJsonMapper jsonMapper) {
            this.delegate = new StdioServerTransportProvider(jsonMapper);
        }

        @Override
        public List<String> protocolVersions() {
            return PROTOCOL_VERSIONS;
        }

        @Override
        public void setSessionFactory(McpServerSession.Factory factory) {
            delegate.setSessionFactory(factory);
        }

        @Override
        public Mono<Void> notifyClients(String method, Object params) {
            return delegate.notifyClients(method, params)
                    .doOnError(e -> log.error("Failed to send notification: {}", e.getMessage()));
        }

        @Override
        public Mono<Void> closeGracefully() {
            return delegate.closeGracefully();
        }
    }
}