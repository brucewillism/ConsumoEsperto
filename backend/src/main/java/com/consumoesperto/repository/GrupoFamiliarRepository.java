package com.consumoesperto.repository;

import com.consumoesperto.model.GrupoFamiliar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GrupoFamiliarRepository extends JpaRepository<GrupoFamiliar, Long> {
}
