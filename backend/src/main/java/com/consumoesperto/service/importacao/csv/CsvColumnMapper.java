package com.consumoesperto.service.importacao.csv;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mapeia cabeçalhos livres para papéis conhecidos, sem depender de um nome único.
 */
public final class CsvColumnMapper {

    public enum Role {
        DATE, DESCRIPTION, MERCHANT, AMOUNT, DEBIT, CREDIT, TYPE, CATEGORY,
        CARD, CARD_LAST4, EXTERNAL_ID, INSTALLMENT, CURRENCY
    }

    private static final Map<String, Role> KEYS = new HashMap<>();

    static {
        map(Role.DATE, "data", "data_da_compra", "data_compra", "data_transacao", "data_lancamento",
            "date", "posted_date", "transaction_date", "dt", "data_movimento");
        map(Role.DESCRIPTION, "descricao", "historico", "details", "detalhe", "detalhes",
            "memo", "lancamento", "title", "titulo", "narrativa");
        map(Role.MERCHANT, "estabelecimento", "merchant", "loja", "favorecido", "beneficiario");
        map(Role.AMOUNT, "valor", "amount", "value", "vlr", "valor_rs", "valor_transacao");
        map(Role.DEBIT, "debito", "debit", "saida", "valor_debito");
        map(Role.CREDIT, "credito", "credit", "entrada", "valor_credito");
        map(Role.TYPE, "tipo", "type", "natureza", "operacao", "d_c");
        map(Role.CATEGORY, "categoria", "category", "classificacao");
        map(Role.CARD, "cartao", "card", "cartao_credito");
        map(Role.CARD_LAST4, "final_do_cartao", "final_cartao", "last4", "final", "cartao_final");
        map(Role.EXTERNAL_ID, "id", "identificador", "id_transacao", "transaction_id", "documento",
            "fitid", "external_id", "id_lancamento");
        map(Role.INSTALLMENT, "parcela", "parcelas", "installment");
        map(Role.CURRENCY, "moeda", "currency");
    }

    private CsvColumnMapper() {}

    private static void map(Role role, String... keys) {
        for (String k : keys) {
            KEYS.put(k, role);
        }
    }

    public static Map<Role, Integer> mapHeader(List<String> header) {
        Map<Role, Integer> out = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String n = CsvHeaderNormalizer.normalize(header.get(i));
            Role role = KEYS.get(n);
            if (role == null) {
                role = inferLoose(n);
            }
            if (role != null && !out.containsKey(role)) {
                out.put(role, i);
            }
        }
        return out;
    }

    static Role inferLoose(String n) {
        if (n.contains("data") && (n.contains("compra") || n.contains("trans") || n.contains("lanc"))) {
            return Role.DATE;
        }
        if (n.equals("data")) {
            return Role.DATE;
        }
        if (n.contains("estabelec") || n.contains("merchant")) {
            return Role.MERCHANT;
        }
        if (n.contains("historico") || n.contains("descricao")) {
            return Role.DESCRIPTION;
        }
        if (n.contains("valor") && !n.contains("iof")) {
            return Role.AMOUNT;
        }
        if (n.contains("debito")) {
            return Role.DEBIT;
        }
        if (n.contains("credito") && !n.contains("cartao")) {
            return Role.CREDIT;
        }
        if (n.contains("final") && n.contains("cartao")) {
            return Role.CARD_LAST4;
        }
        if (n.contains("parcela")) {
            return Role.INSTALLMENT;
        }
        return null;
    }

    public static boolean temColunasMinimas(Map<Role, Integer> map) {
        boolean date = map.containsKey(Role.DATE);
        boolean amount = map.containsKey(Role.AMOUNT)
            || (map.containsKey(Role.DEBIT) && map.containsKey(Role.CREDIT))
            || map.containsKey(Role.DEBIT)
            || map.containsKey(Role.CREDIT);
        boolean desc = map.containsKey(Role.DESCRIPTION) || map.containsKey(Role.MERCHANT);
        return date && amount && desc;
    }

    public static Set<Role> roles() {
        return Set.copyOf(KEYS.values());
    }
}
