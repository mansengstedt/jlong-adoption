package com.example.adoptions.controller;

import com.example.adoptions.model.out.ChatAnswer;
import com.example.adoptions.model.out.ChatMessages;
import com.example.adoptions.service.AdoptionsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.example.adoptions.controller.AdoptionsController.ASSISTANT_PATH;
import static com.example.adoptions.controller.AdoptionsController.CLEAR_MESSAGES_PATH;
import static com.example.adoptions.controller.AdoptionsController.DUMMY_MESSAGES_PATH;
import static com.example.adoptions.controller.AdoptionsController.MESSAGES_PATH;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdoptionsSecurityTests {

    final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdoptionsService adoptionsService;

    @Test
    void assist_ShouldWorkWithoutToken() throws Exception {

        final ChatAnswer answer = ChatAnswer.builder()
                .content("This is a dummy response")
                .build();
        when(adoptionsService.query(anyString(), anyString()))
                .thenReturn(answer);

        mockMvc.perform(get(ASSISTANT_PATH)
                        .param("question", "What is the meaning of life?")
                        .param("user", "testUser"))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(answer)));
    }

    @Test
    void getMessages_ShouldWorkWithoutToken() throws Exception {

        ChatMessages messages = ChatMessages.builder()
                .chatMessages(List.of(
                        ChatMessages.UniformMessage.builder()
                                .id("1")
                                .messageType(MessageType.USER)
                                .content("Hello")
                                .build()))
                .build();

        when(adoptionsService.getChatMessages(anyString()))
                .thenReturn(messages);

        mockMvc.perform(get(MESSAGES_PATH)
                        .param("user", "testUser"))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(messages)));
    }

    @Test
    void clearMessages_ShouldWorkWithoutToken() throws Exception {
        mockMvc.perform(delete(CLEAR_MESSAGES_PATH)
                        .param("user", "testUser"))
                .andExpect(status().isNoContent());
    }

    @Test
    void dummyMessages_WithScopeWrite_ShouldReturnNoContent() throws Exception {
        mockMvc.perform(delete(DUMMY_MESSAGES_PATH)
                        .param("user", "testUser")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_write"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void dummyMessages_WithoutToken_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(delete(DUMMY_MESSAGES_PATH)
                        .param("user", "testUser"))
                .andExpect(status().isForbidden());
    }

    @Test
    void dummyMessages_WithWrongScope_ShouldReturnForbidden() throws Exception {

        mockMvc.perform(delete("/api/adoption/messages/dummy")
                        .param("user", "testUser")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_read"))))
                .andExpect(status().isForbidden());
    }
}
