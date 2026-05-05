package com.consumoesperto.service.entityupdate;

import com.consumoesperto.util.ApelidoNormalizador;

/**
 * Alvo de {@code UPDATE_ENTITY_CONFIG}. CONTA é tratada como cartão neste domínio (sem entidade Conta separada).
 */
public enum UpdateTargetEntity {
    AUTO,
    CONTA,
    CARTAO,
    META,
    CATEGORIA,
    DESPESA_FIXA;

    public static UpdateTargetEntity fromAi(String raw) {
        if (raw == null || raw.isBlank()) {
            return AUTO;
        }
        String u = ApelidoNormalizador.normalizar(raw).replace(' ', '_').replace('-', '_');
        return switch (u) {
            case "conta", "conta_corrente", "banco" -> CONTA;
            case "cartao", "card" -> CARTAO;
            case "meta", "metas", "goal" -> META;
            case "categoria", "categorias", "category" -> CATEGORIA;
            case "despesa_fixa", "despesa_fixas", "recorrente", "fixa" -> DESPESA_FIXA;
            case "auto", "automatic" -> AUTO;
            default -> AUTO;
        };
    }

    public boolean isCartaoOuConta() {
        return this == CARTAO || this == CONTA;
    }
}
