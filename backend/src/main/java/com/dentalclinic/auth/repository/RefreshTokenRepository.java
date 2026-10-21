package com.dentalclinic.auth.repository;

import com.dentalclinic.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    interface TokenReference {
        UUID getTokenId();

        UUID getSessionId();
    }

    @Query("""
            select token.id as tokenId, token.session.id as sessionId
            from RefreshToken token
            where token.tokenHash = :tokenHash
            """)
    Optional<TokenReference> findReferenceByTokenHash(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshToken token where token.id = :tokenId")
    Optional<RefreshToken> findForUpdateById(@Param("tokenId") UUID tokenId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select token
            from RefreshToken token
            where token.session.id = :sessionId
              and token.revokedAt is null
            """)
    List<RefreshToken> findUnrevokedForUpdateBySessionId(@Param("sessionId") UUID sessionId);
}
