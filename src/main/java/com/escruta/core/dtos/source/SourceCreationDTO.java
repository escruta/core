package com.escruta.core.dtos.source;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

public record SourceCreationDTO(
        @NotBlank
        @URL(protocol = "https")
        String link
) {
}
