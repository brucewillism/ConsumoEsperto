package com.consumoesperto.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Metadados estruturados de uma memória semântica (Bloco 2 — memória inteligente).
 * Campos nulos usam os defaults do schema.
 */
public record MemoriaMetadados(
    MemoriaTipo tipo,
    MemoriaOrigem origem,
    BigDecimal confianca,
    LocalDate validade,
    BigDecimal valor,
    String categoria,
    Integer mesAlvo,
    Integer anoAlvo,
    List<Long> transacoesEvidencia
) {

    public static MemoriaMetadados de(MemoriaTipo tipo, MemoriaOrigem origem, BigDecimal confianca) {
        return new MemoriaMetadados(tipo, origem, confianca, null, null, null, null, null, null);
    }

    /** Anotação explícita do usuário — confiança alta. */
    public static MemoriaMetadados anotacaoUsuario(MemoriaOrigem origem) {
        return de(MemoriaTipo.FATO, origem, new BigDecimal("0.90"));
    }

    /** Padrão/fato inferido automaticamente — confiança média, sujeito a confirmação e decaimento. */
    public static MemoriaMetadados inferido(MemoriaTipo tipo) {
        return de(tipo, MemoriaOrigem.INFERIDO, new BigDecimal("0.50"));
    }

    /** Gerada por job (resumo, digest, consolidação). */
    public static MemoriaMetadados sistema(MemoriaTipo tipo) {
        return de(tipo, MemoriaOrigem.SISTEMA, new BigDecimal("0.70"));
    }

    public MemoriaMetadados comValidade(LocalDate v) {
        return new MemoriaMetadados(tipo, origem, confianca, v, valor, categoria, mesAlvo, anoAlvo, transacoesEvidencia);
    }

    public MemoriaMetadados comValor(BigDecimal v) {
        return new MemoriaMetadados(tipo, origem, confianca, validade, v, categoria, mesAlvo, anoAlvo, transacoesEvidencia);
    }

    public MemoriaMetadados comCategoria(String c) {
        return new MemoriaMetadados(tipo, origem, confianca, validade, valor, c, mesAlvo, anoAlvo, transacoesEvidencia);
    }

    public MemoriaMetadados comAlvo(Integer mes, Integer ano) {
        return new MemoriaMetadados(tipo, origem, confianca, validade, valor, categoria, mes, ano, transacoesEvidencia);
    }

    public MemoriaMetadados comEvidencia(List<Long> ids) {
        return new MemoriaMetadados(tipo, origem, confianca, validade, valor, categoria, mesAlvo, anoAlvo, ids);
    }
}
