package com.consumoesperto.model;

/**
 * Ciclo de vida de uma memória semântica. Só {@code ATIVA} entra no RAG/painel.
 */
public enum MemoriaStatus {
    ATIVA,
    /** Evidência que a originou foi excluída/estornada (invalidação retroativa). */
    INVALIDADA,
    /** Contradita por memória mais recente do mesmo tema. */
    SUPERADA,
    /** Expirada, decaída ou consolidada num resumo. */
    ARQUIVADA,
    /** Rejeitada explicitamente pelo usuário. */
    REFUTADA
}
