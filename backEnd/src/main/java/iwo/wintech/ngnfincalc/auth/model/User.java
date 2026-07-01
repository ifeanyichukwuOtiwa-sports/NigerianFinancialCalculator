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
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    // Never expose the password hash via toString (logging, debuggers, error messages).
    @Override
    public String toString() {
        return "User[id=%s, brand=%s, email=%s, fullName=%s, createdAt=%s, updatedAt=%s, passwordHash=***]"
                .formatted(id, brand, email, fullName, createdAt, updatedAt);
    }
}
