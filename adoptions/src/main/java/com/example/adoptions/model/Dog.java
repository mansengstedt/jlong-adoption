package com.example.adoptions.model;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.annotation.Id;

@Schema(description = "A dog available for adoption")
public record Dog(
        @Schema(description = "Unique identifier of the dog", example = "1")
        @Id int id,
        @Schema(description = "Name of the dog", example = "Bella")
        String name,
        @Schema(description = "Current owner of the dog, if any", example = "null")
        String owner,
        @Schema(description = "Description of the dog's breed and temperament", example = "A golden Poodle known for being calm.")
        String description) {
}
