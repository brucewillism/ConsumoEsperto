package com.consumoesperto.repository;

import com.consumoesperto.model.WhatsAppLembretePendencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WhatsAppLembretePendenciaRepository extends JpaRepository<WhatsAppLembretePendencia, Long> {

    boolean existsByTransacaoIdAndTipo(Long transacaoId, String tipo);
}
