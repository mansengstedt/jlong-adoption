package com.example.adoptions.service;

import com.example.adoptions.repository.DogRepository;
import io.modelcontextprotocol.client.McpSyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class AdoptionsService {

    private final ChatClient anthropicAi;

    private final ChatClient geminiAi;

    AdoptionsService(JdbcClient db,
                     DogRepository repository,
                     VectorStore vectorStore,
                     McpSyncClient mcpSyncClient,
                     PromptChatMemoryAdvisor promptChatMemoryAdvisor,
                     ChatClient.Builder ai,
                     @Value("${spring.ai.anthropic.model}") String model) {

        initVectorStore(db, repository, vectorStore);
        this.anthropicAi = initChatClient(vectorStore, mcpSyncClient, promptChatMemoryAdvisor, ai, model);
        this.geminiAi = ai.build(); //fix later
    }

    private void initVectorStore(JdbcClient db,
                                 DogRepository repository,
                                 VectorStore vectorStore) {
        var count = db
                .sql("select count(*) from vector_store")
                .query(Integer.class)
                .single();
        if (count == 0) {
            repository.findAll().forEach(dog -> {
                var dogument = new Document("id: %s, name: %s, description: %s".formatted(
                        dog.id(), dog.name(), dog.description()
                ));
                vectorStore.add(List.of(dogument));
            });
        }
    }

    private ChatClient initChatClient(VectorStore vectorStore,
                                      McpSyncClient mcpSyncClient,
                                      PromptChatMemoryAdvisor promptChatMemoryAdvisor,
                                      ChatClient.Builder ai,
                                      String model) {
        String system = """
                You are an AI powered assistant to help people adopt a dog from the adoption agency named Pooch Palace with locations in Rio de Janeiro, Mexico City, Seoul, Tokyo, Singapore, Paris, Mumbai, New Delhi, Barcelona, London, and San Francisco. Information about the dogs available will be presented below. If there is no information, then return a polite response suggesting we don't have any dogs available.
                """;
        return ai
                .defaultToolCallbacks(new SyncMcpToolCallbackProvider(mcpSyncClient))
                .defaultAdvisors(promptChatMemoryAdvisor, QuestionAnswerAdvisor.builder(vectorStore).build())
                .defaultSystem(system)
                .defaultOptions(ChatOptions.builder()
                        .model(model)
                        .build())
                .build();
    }

    public String query(String user, String question) {
        return anthropicAi
                .prompt()
                .user(question)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, user))
                .call()
                .content();
    }
}
