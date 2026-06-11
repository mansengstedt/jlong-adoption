package com.example.adoptions.model.out;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import org.springframework.ai.chat.messages.MessageType;

import java.util.List;

@Builder
@Schema(description = "Conversation history for a user")
public record ChatMessages(
        @Schema(description = "Messages exchanged between the user and the assistant, in order")
        List<UniformMessage> chatMessages) {

    @Builder
    @Schema(description = "A single message in a conversation")
    public record UniformMessage(
            @Schema(description = "Sequential identifier of the message within the response", example = "1")
            String id,
            @Schema(description = "Text content of the message")
            String content,
            @Schema(description = "Role of the message author")
            MessageType messageType) {}

}
