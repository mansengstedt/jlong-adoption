package com.example.adoptions.service;

import com.example.adoptions.client.ChatClientWithChatMemory;
import com.example.adoptions.config.LazyMcpSyncClient;
import com.example.adoptions.model.out.ChatAnswer;
import com.example.adoptions.model.out.ChatMessages;
import com.example.adoptions.repository.DogRepository;
import com.example.adoptions.tools.DogAdoptionScheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class AdoptionsService {

    private final ChatClientWithChatMemory anthropicAi;
    private final JdbcClient db;

    AdoptionsService(JdbcClient db,
                     DogRepository repository,
                     VectorStore vectorStore,
                     JdbcTemplate jdbcTemplate,
                     LazyMcpSyncClient lazyMcpSyncClient,
                     DogAdoptionScheduler scheduler,
                     ChatMemory chatMemory,
                     ChatClient.Builder ai,
                     @Value("${spring.ai.anthropic.model}") String model) {

        updateVectorStore(db, repository, vectorStore, jdbcTemplate);
        this.anthropicAi = initChatClient(vectorStore, lazyMcpSyncClient, scheduler, chatMemory, ai, model);
        this.db = db;
    }

    private void updateVectorStore(JdbcClient db,
                                   DogRepository repository,
                                   VectorStore vectorStore,
                                   JdbcTemplate jdbcTemplate) {
        var VECTOR_STORE_TABLE_NAME = "vector_store";
        var countVS = db
                .sql("select count(*) from " + VECTOR_STORE_TABLE_NAME)
                .query(Integer.class)
                .single();
        var currentDogs = repository.findAll();

        if (countVS != currentDogs.size()) {
            log.warn("Vector store is NOT up to date. Number of dogs in db: {}, number of dogs in vector store: {}.", currentDogs.size(), countVS);
            jdbcTemplate.execute("TRUNCATE TABLE " + VECTOR_STORE_TABLE_NAME);
            currentDogs.forEach(dog -> {
                var document = new Document("id: %s, name: %s, description: %s".formatted(
                        dog.id(), dog.name(), dog.description()
                ));
                log.info("Adding document: {}", document);
                vectorStore.add(List.of(document));
            });
            log.info("Vector store is now up to date. Number of dogs in db: {}, number of dogs in vector store: {}.", currentDogs.size(), currentDogs.size());
        } else {
            log.info("Vector store is up to date. Number of dogs in db: {}, number of dogs in vector store: {}.", currentDogs.size(), countVS);
        }

        db.sql("SELECT id, content FROM vector_store ORDER BY id")
                .query((rs, rowNum) -> "id=%s, content=%s".formatted(rs.getString("id"), rs.getString("content")))
                .list()
                .forEach(row -> log.info("Vector store entry: {}", row));
    }

    private ChatClientWithChatMemory initChatClient(VectorStore vectorStore,
                                                    LazyMcpSyncClient lazyMcpSyncClient,
                                                    DogAdoptionScheduler scheduler,
                                                    ChatMemory chatMemory,
                                                    ChatClient.Builder ai,
                                                    String model) {
        String system = """
                You are an AI powered assistant to help people adopt a dog from the adoption agency named Pooch Palace
                with locations in Rio de Janeiro, Mexico City, Seoul, Tokyo, Singapore, Paris, Mumbai, New Delhi, Barcelona, London, and San Francisco.
                Information about the dogs available will be presented below.
                If there is no information, then return a polite response suggesting we don't have any dogs available.
                """;
        MessageChatMemoryAdvisor messageChatMemoryAdvisor = MessageChatMemoryAdvisor
                .builder(chatMemory)
                .build();

        var builder = ai
                .defaultAdvisors(messageChatMemoryAdvisor, QuestionAnswerAdvisor.builder(vectorStore).build())
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .defaultSystem(system)
                .defaultOptions(ChatOptions.builder()
                        .model(model)
                        .build());

        if (lazyMcpSyncClient.initialized()) {
            builder.defaultToolCallbacks(SyncMcpToolCallbackProvider.builder()
                    .mcpClients(lazyMcpSyncClient.mcpSyncClient())
                    .build());
        } else {
            log.info("MCP client not initialized, fallback to internal scheduling in {}!!!", scheduler.getClass().getName());
            builder.defaultTools(scheduler);
        }

        return new ChatClientWithChatMemory(builder.build(), chatMemory);
    }

    public ChatAnswer query(String user, String question) {
        return ChatAnswer.builder()
                .content(
                        anthropicAi.chatClient()
                                .prompt()
                                .user(question)
                                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, user))
                                .call()
                                .content())
                .build();
    }

    public ChatMessages getChatMessages(String user) {
        return transform(anthropicAi.chatMemory()
                .get(user), getMessageTimestamps(user));
    }

    public void clearChatMessages(String user) {
        anthropicAi.chatMemory()
                .clear(user);
    }

    /**
     * MessageWindowChatMemory.get() discards the "timestamp" column from
     * SPRING_AI_CHAT_MEMORY, so it's read back directly here, oldest first.
     */
    private List<Instant> getMessageTimestamps(String user) {
        return db.sql("SELECT \"timestamp\" FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = :user ORDER BY \"timestamp\"")
                .param("user", user)
                .query(Timestamp.class)
                .list()
                .stream()
                .map(Timestamp::toInstant)
                .toList();
    }

    private ChatMessages transform(List<Message> messages, List<Instant> timestamps) {
        final AtomicInteger id = new AtomicInteger(0);
        // ChatMemory may window the messages, so only the most recent timestamps line up with them
        final int skip = Math.max(0, timestamps.size() - messages.size());
        return ChatMessages.builder()
                .chatMessages(messages.stream()
                        .map(message -> {
                            int index = id.getAndIncrement();
                            Instant timestamp = index + skip < timestamps.size() ? timestamps.get(index + skip) : null;
                            return ChatMessages.UniformMessage.builder()
                                    .content(message.getText())
                                    .messageType(message.getMessageType())
                                    .id(Integer.valueOf(index + 1).toString())
                                    .timestamp(timestamp)
                                    .build();
                        })
                        .toList())
                .build();
    }
}
