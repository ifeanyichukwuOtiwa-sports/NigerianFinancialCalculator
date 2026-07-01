package iwo.wintech.ngnfincalc.auth.repository;

import iwo.wintech.ngnfincalc.auth.model.User;
import iwo.wintech.ngnfincalc.shared.error.ErrorCode;
import iwo.wintech.ngnfincalc.shared.error.RequestException;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserRepository {

    private final JdbcClient jdbcClient;
    private final Clock clock;

    private static final RowMapper<User> USER_ROW_MAPPER = (rs, rowNum) -> User.builder()
            .id(rs.getLong("id"))
            .brand(rs.getString("brand"))
            .email(rs.getString("email"))
            .passwordHash(rs.getString("password_hash"))
            .fullName(rs.getString("full_name"))
            .createdAt(rs.getObject("created_at", LocalDateTime.class))
            .updatedAt(rs.getObject("updated_at", LocalDateTime.class))
            .build();

    public Optional<User> findById(Long id) {
        return jdbcClient.sql("SELECT id, brand, email, password_hash, full_name, created_at, updated_at FROM users WHERE id = :id")
                .param("id", id)
                .query(USER_ROW_MAPPER)
                .optional();
    }

    public Optional<User> findByBrandAndId(final String brand, final Long id) {
        return jdbcClient.sql("""
                        SELECT id, brand, email, password_hash, full_name, created_at, updated_at
                        FROM users
                        WHERE brand = :brand AND id = :id
                        """)
                .param("brand", brand)
                .param("id", id)
                .query(USER_ROW_MAPPER)
                .optional();
    }

    public Optional<User> findByBrandAndEmail(String brand, String email) {
        return jdbcClient.sql("SELECT id, brand, email, password_hash, full_name, created_at, updated_at FROM users WHERE brand = :brand AND email = :email")
                .param("brand", brand)
                .param("email", email)
                .query(USER_ROW_MAPPER)
                .optional();
    }

    public User save(User user) {
        return user.id() == null ? insertUser(user) : updateUser(user);
    }

    private @NonNull User updateUser(final User user) {
        final LocalDateTime updatedAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        // brand is the tenancy key: immutable (never in SET) and every write is scoped by it (WHERE brand AND id).
        final int rows = jdbcClient.sql("UPDATE users SET email = :email, password_hash = :passwordHash, full_name = :fullName, updated_at = :updatedAt WHERE brand = :brand AND id = :id")
                .param("email", user.email())
                .param("passwordHash", user.passwordHash())
                .param("fullName", user.fullName())
                .param("updatedAt", updatedAt)
                .param("brand", user.brand())
                .param("id", user.id())
                .update();
        if (rows == 0) {
            throw new RequestException("User not found", ErrorCode.USER_NOT_FOUND, Map.of());
        }
        return user.toBuilder().updatedAt(updatedAt).build();
    }

    private User insertUser(final User user) {
        // Columns are MySQL TIMESTAMP(6) (microsecond precision); truncate to match what the DB stores exactly.
        final LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("INSERT INTO users (brand, email, password_hash, full_name, created_at, updated_at) VALUES (:brand, :email, :passwordHash, :fullName, :createdAt, :updatedAt)")
                .param("brand", user.brand())
                .param("email", user.email())
                .param("passwordHash", user.passwordHash())
                .param("fullName", user.fullName())
                .param("createdAt", now)
                .param("updatedAt", now)
                .update(keyHolder);
        final Long id = Objects.requireNonNull(keyHolder.getKeyAs(BigInteger.class)).longValueExact();
        return user.toBuilder().id(id).createdAt(now).updatedAt(now).build();
    }
}
