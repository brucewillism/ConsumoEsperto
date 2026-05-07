package com.consumoesperto.service;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Diferencia padrões de assinatura/serviço de hábitos de consumo para auditoria humana/J.A.R.V.I.S.,
 * sem usar IA nesta decisão — evita chamar “assinatura” a postos, supermercados, etc.
 */
public final class FinanceInsightProfileClassifier {

    public enum Perfil {
        ASSINATURA_SERVICO,
        HABITO_CONSUMO
    }

    private FinanceInsightProfileClassifier() {
    }

    public static Perfil perfilPorDescricao(String descricaoRaw) {
        String n = norm(descricaoRaw);
        if (n.isBlank()) {
            return Perfil.HABITO_CONSUMO;
        }
        // Hábito de consumo: variável, físico/recorrente no dia a dia
        String[] habitos = {
            "posto", "combustivel", "gasolina", "etanol", "shell", "ipiranga", "petrobras",
            "supermercado", " mercado ", " hiper ", "atacadao", "atacadista", "padaria",
            "restaurante", "delivery", "ifood ", " rappi", "burguer ", "lanche",
            "farmacia ", "drogaria", "drogasil", "droga raia", "pacheco",
            "cantina", " cafeteria"
        };
        for (String h : habitos) {
            if (n.contains(h.trim())) {
                return Perfil.HABITO_CONSUMO;
            }
        }
        // Assinatura/telco/software/educação (valor tende a fixo)
        String[] assin = {
            "netflix", "spotify", "amazon prime", "prime video", "disney", "hbo", "globoplay",
            "apple", "icloud", "google one", "microsoft 365", "office 365", "adobe", "github",
            "cursor", "openai", "chatgpt", "dropbox", "notion", "slack", "zoom",
            "telecom", "vivo ", "claro ", "tim ", "oi ", "net ", "internet",
            "academia", "smartfit", "gympass", "eduzz", "hotmart", "stripe", "aws",
            "assinatura mensalidade", " mensalidade", "plano dados", "streaming"
        };
        for (String s : assin) {
            if (n.contains(s.trim())) {
                return Perfil.ASSINATURA_SERVICO;
            }
        }
        return Perfil.HABITO_CONSUMO;
    }

    static String norm(String raw) {
        if (raw == null) {
            return "";
        }
        return Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9 ]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }
}
