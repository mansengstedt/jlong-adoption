package com.example.adoptions.model.out;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "Response returned by the adoption assistant")
public record ChatAnswer(
        @Schema(description = "The assistant's answer to the user's question", example = "We have a calm Poodle named Bella available!")
        String content) {
}
