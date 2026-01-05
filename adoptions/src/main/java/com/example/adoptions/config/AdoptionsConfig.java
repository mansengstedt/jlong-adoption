package com.example.adoptions.config;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class AdoptionsConfig {

    @Bean
    ChatMemory chatMemory(DataSource dataSource) {
        var jdbc = JdbcChatMemoryRepository
                .builder()
                .dataSource(dataSource)
                .build();

        return MessageWindowChatMemory
                .builder()
                .chatMemoryRepository(jdbc)
                .build();
    }

    @Bean
    McpSyncClient mcpSyncClient(@Value("${spring.ai.mcp.client.url}") String url) {
        var mcp = McpClient
                .sync(HttpClientSseClientTransport.builder(url).build()).build();
        mcp.initialize();
        return mcp;
    }

}
