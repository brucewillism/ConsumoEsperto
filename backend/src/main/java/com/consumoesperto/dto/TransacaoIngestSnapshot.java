package com.consumoesperto.dto;

import com.consumoesperto.model.OrigemTransacao;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransacaoIngestSnapshot(
    Long id,
    OrigemTransacao origem,
    String merchantNormalized,
    String merchantRaw,
    String descricao,
    Long categoriaId,
    BigDecimal valor,
    LocalDateTime dataTransacao,
    Long cartaoId,
    Long contaId,
    String externalEventId,
    String fingerprint
) {}
