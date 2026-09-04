package com.example.adoptions.config;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatProperties;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

import javax.sql.DataSource;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class AdoptionsConfig {

    /**
     * Spring AI 1.1.8 supplies temperature=0.8 by default. Claude Sonnet 5
     * rejects that request parameter, so keep the default options but omit it.
     * Defining this bean also makes the Anthropic auto-configuration back off.
     */
    @Bean
    AnthropicChatModel anthropicChatModel(AnthropicApi anthropicApi,
                                          AnthropicChatProperties chatProperties,
                                          RetryTemplate retryTemplate,
                                          ToolCallingManager toolCallingManager) {
        AnthropicChatOptions options = AnthropicChatOptions.fromOptions(chatProperties.getOptions());
        options.setTemperature(null);

        return AnthropicChatModel.builder()
                .anthropicApi(anthropicApi)
                .defaultOptions(options)
                .toolCallingManager(toolCallingManager)
                .retryTemplate(retryTemplate)
                .build();
    }

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
    LazyMcpSyncClient lazyMcpSyncClient(@Value("${spring.ai.mcp.client.url}") String url, @Value("${spring.ai.mcp.client.use-internal-server}") Boolean useInternalServer) {
        log.info("Initializing MCP client with url: {}", url);
        var mcp = McpClient
                .sync(HttpClientSseClientTransport.builder(url)
                        .build())
                .build();
        try {
            mcp.initialize();
            log.info("Initialized MCP client successfully with url: {}", url);
            return new LazyMcpSyncClient(mcp, true);
        } catch (Exception e) {
            if (!useInternalServer) {
                log.error("Server failure since mcp initialization not possible, caused by: {}", e.getMessage(), e);
                throw e;
            } else {
                log.error("Return uninitialized MCP client, fallback needed, WARNING: no external scheduling is possible: {}", e.getMessage(), e);
                return new LazyMcpSyncClient(mcp, false);
            }
        }
    }

}
