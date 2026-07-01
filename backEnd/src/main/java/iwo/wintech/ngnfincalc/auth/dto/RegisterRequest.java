package iwo.wintech.ngnfincalc.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email,

    @NotBlank(message = "Password is required")
    // Cap well under BCrypt's 72-byte limit; bytes past 72 are silently ignored and would not be verified.
    @Size(min = 8, max = 64, message = "Password must be between 8 and 64 characters")
    String password,

    @NotBlank(message = "Full name is required")
    String fullName
) {}
