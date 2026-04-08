package iwo.wintech.ngnfincalc.auth.dto;

public record AuthResponse(
    Long id,
    String email,
    String fullName
) {}
