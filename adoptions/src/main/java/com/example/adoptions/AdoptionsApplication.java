package com.example.adoptions;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.sql.DataSource;
import java.util.List;

@SpringBootApplication
public class AdoptionsApplication {

    static void main(String[] args) {
        SpringApplication.run(AdoptionsApplication.class, args);
    }

    @Bean
    PromptChatMemoryAdvisor promptChatMemoryAdvisor(DataSource dataSource) {
        var jdbc = JdbcChatMemoryRepository
                .builder()
                .dataSource(dataSource)
                .build();

        var chatMessageWindow = MessageWindowChatMemory
                .builder()
                .chatMemoryRepository(jdbc)
                .build();

        return PromptChatMemoryAdvisor
                .builder(chatMessageWindow)
                .build();
    }

    @Bean
    McpSyncClient mcpSyncClient() {
        var mcp = McpClient
                .sync(HttpClientSseClientTransport.builder("http://localhost:8081").build()).build();
        mcp.initialize();
        return mcp;
    }
}

@Controller
@ResponseBody
class AdoptionsController {

    private final ChatClient ai;

    AdoptionsController(JdbcClient db,

                        McpSyncClient mcpSyncClient,
                        PromptChatMemoryAdvisor promptChatMemoryAdvisor,
                        ChatClient.Builder ai,
                        DogRepository repository,
                        VectorStore vectorStore,
                        @Value("${spring.ai.anthropic.model}") String model) {

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
        var system = """
                You are an AI powered assistant to help people adopt a dog from the adoption agency named Pooch Palace with locations in Rio de Janeiro, Mexico City, Seoul, Tokyo, Singapore, Paris, Mumbai, New Delhi, Barcelona, London, and San Francisco. Information about the dogs available will be presented below. If there is no information, then return a polite response suggesting we don't have any dogs available.
                """;
        this.ai = ai
                .defaultToolCallbacks(new SyncMcpToolCallbackProvider(mcpSyncClient))
                .defaultAdvisors(promptChatMemoryAdvisor, QuestionAnswerAdvisor.builder(vectorStore).build())
                .defaultSystem(system)
                .defaultOptions(ChatOptions.builder()
                        .model(model)
                        .build())
                .build();
    }

    @GetMapping("/{user}/assistant")
    String inquire(@PathVariable String user, @RequestParam String question) {
        return ai
                .prompt()
                .user(question)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, user))
                .call()
                .content();

    }
}

interface DogRepository extends ListCrudRepository<Dog, Integer> {
}

record Dog(@Id int id, String name, String owner, String description) {
}
