package iwo.wintech.ngnfincalc.auth.model;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder(toBuilder = true)
public record User(
    Long id,
    String brand,
    String email,
    String passwordHash,
    String fullName,
    LocalDateTime createdAt
) {
}
