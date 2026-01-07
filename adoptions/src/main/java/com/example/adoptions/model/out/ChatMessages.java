package com.example.adoptions.model.out;

import lombok.Builder;
import org.springframework.ai.chat.messages.MessageType;

import java.util.List;

@Builder
public record ChatMessages(List<UniformMessage> chatMessages) {

    @Builder
    public record UniformMessage(String id, String content, MessageType messageType) {}

}
