package com.consumoesperto.repository;

import com.consumoesperto.model.EdithCallbackNonce;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface EdithCallbackNonceRepository extends JpaRepository<EdithCallbackNonce, Long> {

    boolean existsByNonce(String nonce);

    @Modifying
    @Query("DELETE FROM EdithCallbackNonce n WHERE n.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
