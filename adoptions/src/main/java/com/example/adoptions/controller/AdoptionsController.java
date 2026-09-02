package com.example.adoptions.controller;

import com.example.adoptions.model.out.ChatAnswer;
import com.example.adoptions.model.out.ChatMessages;
import com.example.adoptions.service.AdoptionsService;
import com.nimbusds.jwt.JWT;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

import java.text.ParseException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.example.adoptions.controller.AdoptionsController.BASE_PATH;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;

@Controller
@ResponseBody
@RequiredArgsConstructor
@RequestMapping(
        value = BASE_PATH,
        produces = {APPLICATION_JSON_VALUE, APPLICATION_PROBLEM_JSON_VALUE}
)
@Tag(name = "Adoption of dogs", description = "Use vector store and Anthropic LLM API for adoption handling.")
@Validated
@Slf4j
public class AdoptionsController {

    public static final String BASE_PATH = "/api/adoption";

    public static final String ASSISTANT = "/assistant";
    public static final String MESSAGES = "/messages";
    public static final String CLEAR = "/clear";
    public static final String DUMMY = "/dummy";

    public static final String ASSISTANT_PATH = BASE_PATH + ASSISTANT;
    public static final String MESSAGES_PATH = BASE_PATH + MESSAGES;
    public static final String CLEAR_MESSAGES_PATH = BASE_PATH + MESSAGES + CLEAR;
    public static final String DUMMY_MESSAGES_PATH = BASE_PATH + MESSAGES + DUMMY;

    private final AdoptionsService service;

    @Operation(
            summary = "Ask the adoption assistant a question",
            description = "Sends a question to the Anthropic-backed adoption assistant. " +
                    "The assistant uses RAG over the dog catalog and remembers prior " +
                    "messages for the given user. This endpoint is not secured.")
    @ApiResponse(responseCode = "200", description = "Assistant's answer to the question")
    @GetMapping(ASSISTANT)
    @ResponseStatus(HttpStatus.OK)
    ChatAnswer inquire(
            @Parameter(description = "Identifier of the user/conversation", example = "test_user")
            @RequestParam String user,
            @Parameter(description = "Natural language question for the assistant", example = "Do you have any calm dogs?")
            @RequestParam String question) {
        return service.query(user, question);
    }

    @Operation(
            summary = "Get chat history for a user",
            description = "Returns the persisted conversation history for the given user. " +
                    "This endpoint is not secured.")
    @ApiResponse(responseCode = "200", description = "List of chat messages for the user")
    @GetMapping(MESSAGES)
    @ResponseStatus(HttpStatus.OK)
    ChatMessages getMessages(
            @Parameter(description = "Identifier of the user/conversation", example = "test_user")
            @RequestParam String user) {
        return service.getChatMessages(user);
    }

    @Operation(
            summary = "Clear chat history for a user",
            description = "Deletes the persisted conversation history for the given user. " +
                    "This endpoint is not secured.")
    @ApiResponse(responseCode = "200", description = "Chat history cleared the returned messages")
    @ApiResponse(responseCode = "409", description = "No messages can be deleted")
    @DeleteMapping(MESSAGES + CLEAR)
    @ResponseStatus(HttpStatus.OK)
    ChatMessages clearMessages(
            @Parameter(description = "Identifier of the user/conversation", example = "test_user")
            @RequestParam String user) {
        ChatMessages chatMessages = service.getChatMessages(user);
        if (chatMessages == null || chatMessages.chatMessages().isEmpty()) {
            log.warn("No chat history found for user '{}', nothing to clear.", user);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No chat history found for user '" + user + "', nothing to clear.");
        }
        service.clearChatMessages(user);
        return chatMessages;
    }

    @Operation(
            summary = "Demo endpoint for inspecting the JWT/OAuth2 authentication",
            description = "Demonstrates reading the JwtAuthenticationToken and JWT principal. " +
                    "Requires a bearer token with the 'write' scope (SCOPE_write).")
    @ApiResponse(responseCode = "204", description = "Token inspected, no content returned")
    @ApiResponse(responseCode = "401", description = "Unauthorized, lacks valid authentication credentials for the requested resource")
    @ApiResponse(responseCode = "403", description = "Missing or insufficient token scope")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping(MESSAGES + DUMMY)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void dummyMessages(
            JwtAuthenticationToken auth,
            @AuthenticationPrincipal JWT jwt,
            @Parameter(description = "Identifier of the user/conversation", example = "test_user")
            @RequestParam String user) {
        //demo to show jwt settings
        parseAuth(auth);
        parseJwt(jwt);
    }

    private void parseAuth(JwtAuthenticationToken auth) {
        Map<String, Object> map = new LinkedHashMap<>(16);

        map.put("\nmessage", "Protected data!");
        map.put("\nauthorized_client", auth.getName());
        map.put("\nscopes", auth.getAuthorities());
        map.put("\nclaims", auth.getTokenAttributes());
        map.put("\nprincipal", auth.getPrincipal());
        map.put("\ntoken", auth.getToken().getTokenValue());
        map.put("\nisAuthenticated", auth.isAuthenticated() + "");
        map.put("\ndetails", auth.getDetails());
        map.put("\ncredentials", auth.getCredentials());
        map.put("\nclass", auth.getClass());

        Instant now = Instant.now();
        Instant expiresAt = auth.getToken().getExpiresAt();
        log.info("Now: {}, Token expires at: {}, expired = {}", now, expiresAt, expiresAt != null ? expiresAt.isBefore(now) : null);

        log.info("Authentication data: {}", map);
        log.info("Authentication toString: {}", auth);
    }

    /**
     * Does not work, is null, for com.nimbusds.jwt.JWT, and is also not needed when using JwtAuthenticationToken
     *
     * @param jwt the Json Web Token that might be null
     */
    private void parseJwt(JWT jwt) {
        if (jwt == null) {
            log.error("JWT is null!");
            return;
        }
        try {
            // In Client Credentials-flow, 'sub' (subject) is often calling client ID
            String clientId = jwt.getJWTClaimsSet().getSubject();
            // read a claim from the JWT token
            String issuer = jwt.getJWTClaimsSet().getClaimAsString("iss");

            log.info("Client ID: {}, Issuer: {}", clientId, issuer);
        } catch (ParseException e) {
            log.error("Failed to parse JWT!", e);
            throw new RuntimeException(e);
        }


    }
}
