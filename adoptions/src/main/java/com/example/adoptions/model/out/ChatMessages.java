package com.example.adoptions.model.out;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import org.springframework.ai.chat.messages.MessageType;

import java.time.Instant;
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
            @JsonProperty("message_type")
            @Schema(name = "message_type", description = "Role of the message author")
            MessageType messageType,
            @Schema(description = "When the message was originally created")
            Instant timestamp) {}

}
