package com.escruta.core.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CompleteRegistrationRequest(
        @NotBlank @Email String email,
        @NotBlank String verificationToken,
        @NotBlank String name
) {
}
