package com.consumoesperto.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IDs de banco gravados no cadastro (ex.: {@code bb}) e nomes extraídos de PDF/WhatsApp
 * (ex.: {@code Banco do Brasil}) devem resolver para o mesmo emissor.
 */
public final class BancoBrasilCatalog {

    private static final Map<String, List<String>> CANONICAL_ALIASES = buildAliases();

    private BancoBrasilCatalog() {
    }

    /**
     * Indica se dois rótulos referem-se ao mesmo banco (id curto, nome comercial ou alias).
     */
    /** Nome amigável para exibição (ex.: Nu Pagamentos S.A. → Nubank). */
    public static String nomeExibicao(String bancoReferencia) {
        if (bancoReferencia == null || bancoReferencia.isBlank()) {
            return bancoReferencia;
        }
        String id = resolverIdCanonico(ApelidoNormalizador.normalizar(bancoReferencia));
        if (id == null) {
            return bancoReferencia.trim();
        }
        return switch (id) {
            case "nubank" -> "Nubank";
            case "itau" -> "Itaú";
            case "bb" -> "Banco do Brasil";
            case "bradesco" -> "Bradesco";
            case "santander" -> "Santander";
            case "inter" -> "Inter";
            case "c6" -> "C6 Bank";
            case "caixa" -> "Caixa";
            case "mercadopago" -> "Mercado Pago";
            case "xp" -> "XP";
            case "bnb" -> "Banco do Nordeste";
            case "mastercard" -> "Mastercard";
            default -> bancoReferencia.trim();
        };
    }

    public static boolean bancosCorrespondem(String bancoCadastrado, String bancoReferencia) {
        if (bancoCadastrado == null || bancoReferencia == null) {
            return false;
        }
        String a = ApelidoNormalizador.normalizar(bancoCadastrado);
        String b = ApelidoNormalizador.normalizar(bancoReferencia);
        if (a.isBlank() || b.isBlank()) {
            return false;
        }
        if (a.equals(b) || a.contains(b) || b.contains(a)) {
            return true;
        }
        String idA = resolverIdCanonico(a);
        String idB = resolverIdCanonico(b);
        if (idA != null && idB != null) {
            return idA.equals(idB);
        }
        if (idA != null) {
            return aliasCanonicoContem(idA, b);
        }
        if (idB != null) {
            return aliasCanonicoContem(idB, a);
        }
        return false;
    }

    private static String resolverIdCanonico(String normalizado) {
        for (Map.Entry<String, List<String>> entry : CANONICAL_ALIASES.entrySet()) {
            if (entry.getKey().equals(normalizado)) {
                return entry.getKey();
            }
            for (String alias : entry.getValue()) {
                if (ApelidoNormalizador.normalizar(alias).equals(normalizado)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private static boolean aliasCanonicoContem(String canonicalId, String normalizadoReferencia) {
        List<String> aliases = CANONICAL_ALIASES.get(canonicalId);
        if (aliases == null) {
            return false;
        }
        for (String alias : aliases) {
            String n = ApelidoNormalizador.normalizar(alias);
            if (n.isBlank()) {
                continue;
            }
            if (n.equals(normalizadoReferencia)
                || normalizadoReferencia.contains(n)
                || n.contains(normalizadoReferencia)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, List<String>> buildAliases() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("nubank", List.of("nubank", "nu bank", "nu", "nu pagamentos", "nu pagamentos sa"));
        m.put("itau", List.of("itau", "itaú", "itau unibanco", "banco itau", "itau azul", "cartao itau"));
        m.put("inter", List.of("inter", "banco inter"));
        m.put("bradesco", List.of("bradesco", "banco bradesco"));
        m.put("santander", List.of("santander", "banco santander"));
        m.put("bb", List.of("bb", "banco do brasil", "banco brasil"));
        m.put("caixa", List.of("caixa", "caixa economica", "cef", "caixa economica federal"));
        m.put("c6", List.of("c6", "c6 bank"));
        m.put("btg", List.of("btg", "btg pactual"));
        m.put("pagbank", List.of("pagbank", "pag bank", "pagseguro"));
        m.put("mercadopago", List.of("mercadopago", "mercado pago", "cartao de credito mercado pago"));
        m.put("xp", List.of("xp", "xp investimentos", "cartao xp"));
        m.put("bnb", List.of("bnb", "banco do nordeste", "bnbank"));
        m.put("mastercard", List.of("mastercard", "master card"));
        m.put("sicredi", List.of("sicredi"));
        m.put("sicoob", List.of("sicoob"));
        m.put("neon", List.of("neon", "banco neon"));
        m.put("original", List.of("original", "banco original"));
        m.put("pan", List.of("pan", "banco pan"));
        m.put("safra", List.of("safra", "banco safra"));
        m.put("banrisul", List.of("banrisul"));
        m.put("outros", List.of("outro", "outros"));
        return Map.copyOf(m);
    }
}
