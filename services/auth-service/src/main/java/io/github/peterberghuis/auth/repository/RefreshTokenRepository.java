package io.github.peterberghuis.auth.repository;

import io.github.peterberghuis.auth.entity.RefreshToken;
import io.github.peterberghuis.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByToken(String token);

    @Modifying
    void deleteByUser(User user);

    @Modifying
    @Query(value = """
            INSERT INTO auth_schema.refresh_tokens (id, token, user_id, expiry_date)
            VALUES (:id, :token, :userId, :expiryDate)
            ON CONFLICT (user_id)
            DO UPDATE SET
                token = EXCLUDED.token,
                expiry_date = EXCLUDED.expiry_date,
                id = EXCLUDED.id
            """, nativeQuery = true)
    void upsertRefreshToken(@Param("id") UUID id, @Param("token") String token, @Param("userId") UUID userId, @Param("expiryDate") Instant expiryDate);
}
