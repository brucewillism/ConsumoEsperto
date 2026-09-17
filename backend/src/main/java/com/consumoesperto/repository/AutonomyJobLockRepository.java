package com.consumoesperto.repository;

import com.consumoesperto.model.AutonomyJobLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

public interface AutonomyJobLockRepository extends JpaRepository<AutonomyJobLock, String> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("UPDATE AutonomyJobLock l SET l.lockedUntil = :until "
        + "WHERE l.jobName = :jobName AND l.lockedBy = :lockedBy")
    int renew(
        @Param("jobName") String jobName,
        @Param("until") LocalDateTime until,
        @Param("lockedBy") String lockedBy
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("UPDATE AutonomyJobLock l SET l.lockedUntil = :until, l.lockedBy = :lockedBy "
        + "WHERE l.jobName = :jobName AND (l.lockedUntil IS NULL OR l.lockedUntil <= :now)")
    int stealExpired(
        @Param("jobName") String jobName,
        @Param("until") LocalDateTime until,
        @Param("lockedBy") String lockedBy,
        @Param("now") LocalDateTime now
    );
}
