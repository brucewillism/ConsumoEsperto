package com.consumoesperto.repository;

import com.consumoesperto.model.FinancialDomainEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface FinancialDomainEventRepository extends JpaRepository<FinancialDomainEvent, Long> {

    List<FinancialDomainEvent> findTop50ByProcessedFalseOrderByCreatedAtAsc();

    @Query("SELECT e FROM FinancialDomainEvent e WHERE "
        + "((e.processed = false AND (e.processingStatus IS NULL "
        + "OR e.processingStatus IN ('PENDING', 'FAILED_RETRYABLE', 'PROCESSED')) "
        + "AND (e.nextRetryAt IS NULL OR e.nextRetryAt <= :now)) "
        + "OR (e.processingStatus = 'PROCESSING' "
        + "AND (e.lastAttemptAt IS NULL OR e.lastAttemptAt <= :staleBefore))) "
        + "ORDER BY e.createdAt ASC")
    List<FinancialDomainEvent> findClaimable(
        @Param("now") LocalDateTime now,
        @Param("staleBefore") LocalDateTime staleBefore,
        Pageable pageable
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("UPDATE FinancialDomainEvent e SET e.processingStatus = 'PROCESSING', "
        + "e.lastAttemptAt = :now, e.attemptCount = e.attemptCount + 1 "
        + "WHERE e.id = :id AND ("
        + "(e.processed = false AND (e.processingStatus IS NULL "
        + "OR e.processingStatus IN ('PENDING', 'FAILED_RETRYABLE', 'PROCESSED')) "
        + "AND (e.nextRetryAt IS NULL OR e.nextRetryAt <= :now)) "
        + "OR (e.processingStatus = 'PROCESSING' "
        + "AND (e.lastAttemptAt IS NULL OR e.lastAttemptAt <= :staleBefore)))")
    int claimForProcessing(
        @Param("id") Long id,
        @Param("now") LocalDateTime now,
        @Param("staleBefore") LocalDateTime staleBefore
    );
}
