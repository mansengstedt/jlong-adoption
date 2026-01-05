package com.example.adoptions.controller;

import com.example.adoptions.model.out.ChatMessages;
import com.example.adoptions.service.AdoptionsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
@ResponseBody
@RequiredArgsConstructor
class AdoptionsController {

    private final AdoptionsService service;

    @GetMapping("/{user}/assistant")
    @ResponseStatus(HttpStatus.OK)
    String inquire(@PathVariable String user, @RequestParam String question) {
        return service.query(user, question);
    }

    @GetMapping("/{user}/messages")
    @ResponseStatus(HttpStatus.OK)
    ChatMessages getMessages(@PathVariable String user) {
        return service.getChatMessages(user);
    }

    @DeleteMapping("/{user}/messages/clear")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void clearMessages(@PathVariable String user) {
        service.clearChatMessages(user);
    }
}
