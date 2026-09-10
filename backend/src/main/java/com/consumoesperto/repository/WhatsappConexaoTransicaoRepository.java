package com.consumoesperto.repository;

import com.consumoesperto.model.WhatsappConexaoTransicao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WhatsappConexaoTransicaoRepository extends JpaRepository<WhatsappConexaoTransicao, Long> {

    List<WhatsappConexaoTransicao> findTop20ByUsuarioIdOrderByVerificadoEmDesc(Long usuarioId);
}
