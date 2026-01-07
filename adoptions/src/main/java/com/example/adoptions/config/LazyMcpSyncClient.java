package com.example.adoptions.config;

import io.modelcontextprotocol.client.McpSyncClient;

public record LazyMcpSyncClient(McpSyncClient mcpSyncClient, Boolean initialized) {
}
