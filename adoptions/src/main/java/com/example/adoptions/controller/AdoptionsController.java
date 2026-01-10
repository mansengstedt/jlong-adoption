package com.example.adoptions.controller;

import com.example.adoptions.model.out.ChatAnswer;
import com.example.adoptions.model.out.ChatMessages;
import com.example.adoptions.service.AdoptionsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

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

    @GetMapping(ASSISTANT)
    @ResponseStatus(HttpStatus.OK)
    ChatAnswer inquire(@RequestParam String user, @RequestParam String question) {
        return service.query(user, question);
    }

    @GetMapping(MESSAGES)
    @ResponseStatus(HttpStatus.OK)
    ChatMessages getMessages(@RequestParam String user) {
        return service.getChatMessages(user);
    }

    @DeleteMapping(MESSAGES + CLEAR)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void clearMessages(@RequestParam String user) {
        service.clearChatMessages(user);
    }

    @DeleteMapping(MESSAGES + DUMMY)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void dummyMessages(@RequestParam String user) {
        log.info("Calling dummy messages for user {}", user);
    }
}
