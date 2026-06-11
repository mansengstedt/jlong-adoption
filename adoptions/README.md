# Adoptions Service

A Spring Boot demo application for "Pooch Palace", a fictional dog adoption agency with
locations in Rio de Janeiro, Mexico City, Seoul, Tokyo, Singapore, Paris, Mumbai, New Delhi,
Barcelona, London, and San Francisco. The service exposes a conversational AI assistant
(backed by Anthropic Claude via Spring AI) that can answer questions about dogs available
for adoption and schedule/unschedule pickup appointments.

## 1. Overview of functionality

- The `dog` table holds the catalog of adoptable animals (id, name, owner, description).
- A user asks natural-language questions (e.g. "Do you have any calm poodles?") via the
  `/api/adoption/assistant` endpoint.
- The assistant uses Retrieval Augmented Generation (RAG): dog descriptions are embedded
  into a vector store, and relevant dogs are retrieved and injected into the prompt sent
  to the Anthropic Claude model.
- The assistant remembers prior conversation turns per user (chat memory), so follow-up
  questions ("can I schedule a visit for that one?") work in context.
- The assistant can call tools to schedule or unschedule an adoption appointment, either
  via an internal tool implementation or via an external MCP (Model Context Protocol)
  scheduler service.

## 2. Technical details

### Frameworks & language

- **Java 25**, built with **Maven** (`pom.xml`), Spring Boot **3.5.9** parent.
- **Spring AI 1.1.2** (BOM-managed) for LLM orchestration:
  - `spring-ai-starter-model-anthropic` — Anthropic Claude chat model integration.
  - `spring-ai-starter-vector-store-pgvector` — pgvector-backed `VectorStore`.
  - `spring-ai-starter-model-postgresml-embedding` — embeddings computed via PostgresML.
  - `spring-ai-advisors-vector-store` — `QuestionAnswerAdvisor` for RAG.
  - `spring-ai-starter-model-chat-memory-repository-jdbc` — JDBC-backed chat memory.
  - `spring-ai-starter-mcp-client` — MCP client for an external scheduler tool server.
- **Spring Data JDBC** (`spring-boot-starter-data-jdbc`) for the `Dog` repository.
- **Spring Security + OAuth2 Resource Server** for JWT-based endpoint protection.
- **Spring Boot Actuator** for health/metrics/info endpoints.
- **Lombok** for boilerplate (builders, logging, constructors).
- **H2** is included as a runtime dependency (not used as the primary datastore — see below).
- GraalVM native-image build plugin is configured for native compilation.

### Database initialization

- `application.yml` points the datasource at a local PostgreSQL/PostgresML instance:
  `jdbc:postgresql://localhost:5433/postgresml` (user `myappuser` / `mypassword`).
- `spring.sql.init.mode: always` causes Spring Boot to run
  [schema.sql](src/main/resources/schema.sql) and [data.sql](src/main/resources/data.sql)
  on every startup:
  - `schema.sql` drops and recreates the `dog` table (`id`, `name`, `owner`, `description`).
  - `data.sql` upserts (`ON CONFLICT ... DO UPDATE`) a fixed set of seed dogs (and a couple
    of cats/wolves for fun).
- The [db/](db) folder contains setup notes and scripts for standing up the PostgresML
  Docker container and creating the `myappuser` role/database (`db/users.sql`,
  `db/README_POSTGRES.md`). See section 5 for the full local startup sequence.

### Vector store initialization

- `spring.ai.vectorstore.pgvector` is configured with `dimensions: 768` and
  `initialize-schema: true`, so Spring AI creates/manages a `vector_store` table in
  Postgres automatically using the pgvector extension.
- Embeddings are produced via PostgresML (`spring.ai.postgresml.embedding`,
  `create-extension: true`, `vector-type: pg_vector`).
- On startup, [AdoptionsService](src/main/java/com/example/adoptions/service/AdoptionsService.java)
  (`updateVectorStore`) compares the row count of `vector_store` against the number of
  dogs in the `dog` table:
  - If they differ, it **truncates** `vector_store` and re-embeds every dog as a
    `Document` of the form `"id: <id>, name: <name>, description: <description>"`.
  - If they match, it logs that the vector store is already up to date.
- This keeps the vector store in sync whenever `data.sql` is changed and the app restarts.

### Anthropic chat client initialization

- `spring.ai.anthropic.api-key` is read from the `ANTHROPIC_CONNECTION_KEY` environment
  variable (a dummy placeholder `NOT_NEEDED` is used if unset, just to avoid startup
  errors — the real key is expected to be supplied via secrets/env at runtime).
- `spring.ai.anthropic.model` is set to `claude-sonnet-4-6`.
- `AdoptionsService.initChatClient(...)` builds a `ChatClient` (Spring AI) configured with:
  - A **system prompt** describing the assistant's role (Pooch Palace adoption helper).
  - `PromptChatMemoryAdvisor` — injects prior conversation history into the prompt
    (see chat memory section below).
  - `QuestionAnswerAdvisor` (vector store advisor) — performs RAG by querying the
    `VectorStore` for dog documents relevant to the user's question and adding them to
    the prompt context.
  - `SimpleLoggerAdvisor` — logs the request/response for debugging
    (`org.springframework.ai.chat.client*` loggers are set to `DEBUG`).
  - `ChatOptions` with the model name from configuration.
  - Either MCP-based tool callbacks or the local `DogAdoptionScheduler` tool, depending
    on whether the MCP client initialized successfully (see MCP section below).
- The resulting `ChatClient` plus the `ChatMemory` bean are wrapped in a
  `ChatClientWithChatMemory` record and stored on the service.

## 3. Chat memory (`ChatClientWithChatMemory`)

[ChatClientWithChatMemory](src/main/java/com/example/adoptions/client/ChatClientWithChatMemory.java)
is a simple record pairing the configured `ChatClient` with the `ChatMemory` instance used
to back it:

```java
public record ChatClientWithChatMemory(ChatClient chatClient, ChatMemory chatMemory) {}
```

- The `ChatMemory` bean is defined in
  [AdoptionsConfig](src/main/java/com/example/adoptions/config/AdoptionsConfig.java) as a
  `MessageWindowChatMemory` backed by a `JdbcChatMemoryRepository` (table(s) auto-created
  via `spring.ai.chat.memory.repository.jdbc.initialize-schema: always`). This means
  conversation history is persisted in Postgres, not just in memory.
- `AdoptionsService` uses this pairing for three purposes:
  - **`query(user, question)`** — calls the chat client with
    `advisors(a -> a.param(ChatMemory.CONVERSATION_ID, user))`. The `PromptChatMemoryAdvisor`
    uses the `user` value as the conversation ID to load/store history per user, so each
    user has an independent, persisted conversation thread.
  - **`getChatMessages(user)`** — reads the raw message list from `chatMemory.get(user)`
    and transforms it into the `ChatMessages` DTO (id, content, message type) for API
    responses.
  - **`clearChatMessages(user)`** — calls `chatMemory.clear(user)` to wipe a user's
    conversation history.
- Bundling the `ChatClient` and `ChatMemory` together in one record avoids passing two
  separate beans around and keeps the "memory + AI" concept cohesive in the service layer.

## 4. REST endpoints

All endpoints are defined in
[AdoptionsController](src/main/java/com/example/adoptions/controller/AdoptionsController.java)
under base path **`/api/adoption`**, producing `application/json` /
`application/problem+json`.

| Method | Path | In parameters | Out (response) | Security |
|---|---|---|---|---|
| GET | `/api/adoption/assistant` | Query params: `user` (String), `question` (String) | `200 OK`, [ChatAnswer](src/main/java/com/example/adoptions/model/out/ChatAnswer.java) — `{ "content": string }` | **Unsecured** (`permitAll`) |
| GET | `/api/adoption/messages` | Query param: `user` (String) | `200 OK`, [ChatMessages](src/main/java/com/example/adoptions/model/out/ChatMessages.java) — `{ "chatMessages": [{ "id": string, "content": string, "messageType": enum }] }` | **Unsecured** (`permitAll`) |
| DELETE | `/api/adoption/messages/clear` | Query param: `user` (String) | `204 No Content` | **Unsecured** (`permitAll`) |
| DELETE | `/api/adoption/messages/dummy` | Query param: `user` (String); requires a Bearer JWT | `204 No Content` | **Secured** — requires `SCOPE_write` authority (OAuth2/JWT) |

Notes on `/api/adoption/messages/dummy`:
- This endpoint exists purely as a **demonstration of JWT/OAuth2 plumbing**. It accepts the
  injected `JwtAuthenticationToken` and `@AuthenticationPrincipal JWT`, logs the token's
  claims/scopes/principal/expiry, and returns no content.
- A request without a valid token (or without the `SCOPE_write` authority) returns
  **403 Forbidden**.

Additionally, Actuator endpoints are exposed and unsecured:
- `/actuator/health` (with details and liveness/readiness probe groups)
- `/actuator/info`
- `/actuator/metrics`
- `/actuator/prometheus`

Swagger/OpenAPI paths (`/swagger/**`, `/swagger-ui/**`, `/swagger-ui.html`,
`/v3/api-docs/**`) are also excluded from authentication.

### API documentation (Swagger UI)

The API is documented with springdoc-openapi/Swagger annotations on the controller and
DTO classes ([OpenApiConfig](src/main/java/com/example/adoptions/config/OpenApiConfig.java)
defines a `bearerAuth` JWT security scheme for the secured endpoint). When the application
is running locally, the interactive docs are available at:

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

### Security configuration summary

Defined in
[SecurityConfig](src/main/java/com/example/adoptions/config/SecurityConfig.java):

- CSRF is disabled.
- Order of rules:
  1. `/api/adoption/messages/dummy` requires authority `SCOPE_write`.
  2. `/api/adoption/assistant`, `/api/adoption/messages`, and
     `/api/adoption/messages/clear` are explicitly `permitAll`.
  3. `/swagger/**`, `/swagger-ui/**`, `/actuator/**` are `permitAll`.
  4. Everything else requires authentication (`anyRequest().authenticated()`).
- The app is configured as an **OAuth2 Resource Server** validating JWTs
  (`oauth2ResourceServer().jwt(...)`).
- `JwtDecoder` is built from the issuer URI `http://localhost:9001`
  (`spring.security.oauth2.resourceserver.jwt.issuer-uri` in `application.yml`), with clock
  skew tolerance set to zero.
- If `spring-boot-starter-security` and `spring-boot-starter-oauth2-resource-server` were
  removed from `pom.xml` *and* this config class removed, all endpoints would be
  unsecured. Removing only the config class (but keeping the dependencies) results in
  `401 Unauthorized` on all endpoints.

## 5. External services

This application depends on two companion services running locally for full
functionality (see [db/README_POSTGRES.md](db/README_POSTGRES.md)):

1. **PostgresML / Postgres database** (port `5433`)
   - Hosts the `dog` table, the `vector_store` (pgvector) table, and the JDBC chat memory
     tables.
   - Set up via Docker (`db/run.sh`, `db/init.sh`) and `db/users.sql`, which creates the
     `myappuser` role and grants needed privileges (including on the `pgml` schema for
     PostgresML embeddings).

2. **Auth server** (port `9001`) — a separate Spring Authorization Server project
   (`IdeaProjects/oauth/oauth-server/auth`, `local-h2` profile).
   - Issues JWTs validated by this app's OAuth2 resource server configuration
     (`issuer-uri: http://localhost:9001`).
   - Required at startup because the resource server JWT decoder resolves issuer metadata
     from this URL — if it's unreachable, the app may fail to start or fail to validate
     tokens.

3. **Scheduler service** (port `8081`) — a separate project
   (`IdeaProjects/ai/jlong/2025-05-16-anthropic/scheduler`).
   - Acts as an external **MCP server** exposing scheduling tools over HTTP/SSE.
   - Configured via `spring.ai.mcp.client.url: http://localhost:8081` and
     `use-internal-server: true`.

### Startup order (local dev)

Per `db/README_POSTGRES.md`, for the assistant to fully work end-to-end:
1. Start/initialize the Postgres/PostgresML Docker container (`run.sh`/`init.sh`,
   `users.sql`).
2. Start the **scheduler** application (port 8081) — needed for MCP-based tool calls and
   for loading data if `data.sql` changes.
3. Start the **auth server** (port 9001) — needed because the resource server config
   requires a reachable issuer, even though most adoption endpoints are unsecured.
4. Start this `adoptions` application.

## 6. MCP (Model Context Protocol) details

- **Dependency**: `spring-ai-starter-mcp-client`.
- **Configuration** (`application.yml`):
  ```yaml
  spring:
    ai:
      mcp:
        client:
          url: http://localhost:8081
          use-internal-server: true
  ```
- **Bean**: `lazyMcpSyncClient` in
  [AdoptionsConfig](src/main/java/com/example/adoptions/config/AdoptionsConfig.java):
  - Builds a synchronous MCP client (`McpClient.sync(...)`) using an SSE transport
    (`HttpClientSseClientTransport`) pointed at the configured `url`.
  - Calls `mcp.initialize()` at startup.
  - On success, returns a [LazyMcpSyncClient](src/main/java/com/example/adoptions/config/LazyMcpSyncClient.java)
    record with `initialized = true`.
  - On failure:
    - If `use-internal-server` is `false`, the exception is rethrown and the application
      **fails to start**.
    - If `use-internal-server` is `true` (the default here), the error is logged and an
      **uninitialized** `LazyMcpSyncClient` (`initialized = false`) is returned, allowing
      the app to start without the external scheduler.
- **Tool wiring** in `AdoptionsService.initChatClient(...)`:
  - If `lazyMcpSyncClient.initialized()` is `true`, the chat client's tool callbacks are
    set to `SyncMcpToolCallbackProvider(lazyMcpSyncClient.mcpSyncClient())` — i.e. the
    Claude model can invoke scheduling tools exposed by the **external MCP scheduler
    service** (port 8081).
  - If `false`, the chat client falls back to the **internal**
    [DogAdoptionScheduler](src/main/java/com/example/adoptions/tools/DogAdoptionScheduler.java)
    `@Tool`-annotated component, which provides:
    - `schedule(int dogId, String dogName)` — "schedules" an adoption pickup 3 days from
      now and returns the date.
    - `unschedule(int dogId, String dogName, String scheduledDay)` — "unschedules" a
      previously scheduled pickup.
  - In both cases, the model decides (via tool-calling) when to invoke these
    schedule/unschedule tools based on the conversation.

## 7. Logging & observability

- Debug logging is enabled for `org.springframework.ai.chat.client` and
  `org.springframework.ai.chat.client.advisor`, useful for inspecting prompts, RAG
  context, and tool calls (via `SimpleLoggerAdvisor`).
- Actuator health checks include a custom readiness group noting the service "cannot work
  without a DB".
