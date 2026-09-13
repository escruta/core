package com.escruta.core.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record VerifyEmailCodeRequest(
        @NotBlank @Email String email,
        @NotBlank String code
) {
}
