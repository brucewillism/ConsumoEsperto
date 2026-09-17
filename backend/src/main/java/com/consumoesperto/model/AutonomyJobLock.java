package com.consumoesperto.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "autonomy_job_lock")
@Getter
@Setter
@NoArgsConstructor
public class AutonomyJobLock {

    @Id
    @Column(name = "job_name", length = 64)
    private String jobName;

    @Column(name = "locked_until", nullable = false)
    private LocalDateTime lockedUntil;

    @Column(name = "locked_by", length = 64)
    private String lockedBy;
}
