package iwo.wintech.ngnfincalc.auth.repository;

import iwo.wintech.ngnfincalc.auth.model.User;
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
            .build();

    public Optional<User> findById(Long id) {
        return jdbcClient.sql("SELECT id, brand, email, password_hash, full_name, created_at FROM users WHERE id = :id")
                .param("id", id)
                .query(USER_ROW_MAPPER)
                .optional();
    }

    public Optional<User> findByBrandAndId(final String brand, final Long id) {
        return jdbcClient.sql("""
                        SELECT id, brand, email, password_hash, full_name, created_at
                        FROM users
                        WHERE brand = :brand AND id = :id
                        """)
                .param("brand", brand)
                .param("id", id)
                .query(USER_ROW_MAPPER)
                .optional();
    }

    public Optional<User> findByBrandAndEmail(String brand, String email) {
        return jdbcClient.sql("SELECT id, brand, email, password_hash, full_name, created_at FROM users WHERE brand = :brand AND email = :email")
                .param("brand", brand)
                .param("email", email)
                .query(USER_ROW_MAPPER)
                .optional();
    }

    public User save(User user) {
        return user.id() == null ? insertUser(user) : updateUser(user);
    }

    private @NonNull User updateUser(final User user) {
        jdbcClient.sql("UPDATE users SET brand = :brand, email = :email, password_hash = :passwordHash, full_name = :fullName WHERE id = :id")
                .param("brand", user.brand())
                .param("email", user.email())
                .param("passwordHash", user.passwordHash())
                .param("fullName", user.fullName())
                .param("id", user.id())
                .update();
        return user;
    }

    private User insertUser(final User user) {
        final LocalDateTime createdAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("INSERT INTO users (brand, email, password_hash, full_name, created_at) VALUES (:brand, :email, :passwordHash, :fullName, :createdAt)")
                .param("brand", user.brand())
                .param("email", user.email())
                .param("passwordHash", user.passwordHash())
                .param("fullName", user.fullName())
                .param("createdAt", createdAt)
                .update(keyHolder);
        final Long id = Objects.requireNonNull(keyHolder.getKeyAs(BigInteger.class)).longValueExact();
        return user.toBuilder().id(id).createdAt(createdAt).build();
    }
}
