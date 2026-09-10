package com.consumoesperto.repository;

import com.consumoesperto.model.EdithToolAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EdithToolAuditRepository extends JpaRepository<EdithToolAudit, Long> {

    List<EdithToolAudit> findByToolCallId(String toolCallId);

    @Modifying
    @Query("DELETE FROM EdithToolAudit a WHERE a.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") LocalDateTime cutoff);
}
