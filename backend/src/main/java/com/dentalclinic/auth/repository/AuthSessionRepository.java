package com.dentalclinic.auth.repository;

import com.dentalclinic.auth.entity.AuthSession;
import com.dentalclinic.auth.entity.AuthSessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from AuthSession session where session.id = :sessionId")
    Optional<AuthSession> findForUpdateById(@Param("sessionId") UUID sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select session
            from AuthSession session
            where session.userId = :userId
              and session.status <> :revokedStatus
            """)
    List<AuthSession> findActiveForUpdateByUserId(
            @Param("userId") UUID userId,
            @Param("revokedStatus") AuthSessionStatus revokedStatus
    );
}
