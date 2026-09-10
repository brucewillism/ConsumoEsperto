package com.consumoesperto.ingest.notificacao.parser;

import com.consumoesperto.mobilecapture.parser.MobileMoneyParser;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpreta o texto de notificações Nubank e Itaú. Reutiliza {@link MobileMoneyParser} (pt-BR).
 * Formatos de banco mudam — ver {@code src/test/resources/notificacoes-bancarias.jsonl}.
 */
@Component
public class NotificacaoBancariaParser {

    public static final String CANAL_CREDITO = "CREDITO";
    public static final String CANAL_DEBITO = "DEBITO";
    public static final String CANAL_PIX = "PIX";

    private static final Pattern MERCHANT_EM = Pattern.compile(
        "(?i)(?:em|no|na)\\s+(.{2,120}?)(?:\\s+no\\s+cart[aã]o.*)?$");
    private static final Pattern MERCHANT_PARA = Pattern.compile(
        "(?i)para\\s+(.{2,80})$");
    private static final Pattern MERCHANT_DE = Pattern.compile(
        "(?i)\\bde\\s+([\\p{L}][\\p{L}0-9 .&'-]{1,80})$");

    private static final List<Pattern> DESCARTE = List.of(
        Pattern.compile("(?i)fatura\\s+fechou"),
        Pattern.compile("(?i)vencimento\\s+da\\s+fatura"),
        Pattern.compile("(?i)saldo\\s+disponivel"),
        Pattern.compile("(?i)limite\\s+disponivel"),
        Pattern.compile("(?i)login\\s+detectado"),
        Pattern.compile("(?i)novo\\s+acesso"),
        Pattern.compile("(?i)novo\\s+login"),
        Pattern.compile("(?i)promoc"),
        Pattern.compile("(?i)aproveite"),
        Pattern.compile("(?i)cashback\\s+neste"),
        Pattern.compile("(?i)anuidade"),
        Pattern.compile("(?i)convite"),
        Pattern.compile("(?i)lembrete:\\s*pague"),
        Pattern.compile("(?i)voce\\s+tem\\s+r\\$"),
        Pattern.compile("(?i)compra\\s+recusada"),
        Pattern.compile("(?i)transacao\\s+nao\\s+autorizada")
    );

    private static final List<Pattern> ESTORNO = List.of(
        Pattern.compile("(?i)\\bestorno\\b"),
        Pattern.compile("(?i)compra\\s+cancelada"),
        Pattern.compile("(?i)transacao\\s+cancelada"),
        Pattern.compile("(?i)cancelamento\\s+de\\s+compra"),
        Pattern.compile("(?i)\\breembolso\\b")
    );

    public String normalizarApp(String app, String titulo) {
        String blob = normalize(join(app, titulo));
        if (blob.contains("nubank") || blob.contains("nu bank") || blob.equals("nu") || blob.contains("com.nu")) {
            return "nubank";
        }
        if (blob.contains("itau") || blob.contains("itaú")) {
            return "itau";
        }
        String raw = app == null ? "" : app.trim().toLowerCase(Locale.ROOT);
        if (raw.isBlank() || "outro".equals(raw)) {
            return "outro";
        }
        return raw.replaceAll("[^a-z0-9]", "");
    }

    public ParsedNotificacaoBancaria parse(String app, String titulo, String texto) {
        String banco = normalizarApp(app, titulo);
        String combined = join(titulo, texto);
        String norm = normalize(combined);

        if (combined.isBlank()) {
            return ParsedNotificacaoBancaria.naoReconhecida("texto vazio");
        }
        for (Pattern p : DESCARTE) {
            if (p.matcher(norm).find()) {
                return ParsedNotificacaoBancaria.ignorar("padrao_descarte");
            }
        }

        Optional<BigDecimal> valorOpt = MobileMoneyParser.firstAmount(texto, titulo);
        boolean estorno = ESTORNO.stream().anyMatch(p -> p.matcher(norm).find());
        if (estorno) {
            if (valorOpt.isEmpty()) {
                return ParsedNotificacaoBancaria.naoReconhecida("estorno_sem_valor");
            }
            String merchant = extractMerchant(combined);
            return ParsedNotificacaoBancaria.estorno(
                valorOpt.get(),
                merchant,
                MobileMoneyParser.normalizeMerchant(merchant),
                inferCanal(norm, banco)
            );
        }

        if (valorOpt.isEmpty()) {
            return ParsedNotificacaoBancaria.naoReconhecida("sem_valor");
        }

        String tipo;
        String canal;
        String tipoTx = "DESPESA";
        if (isPixRecebido(norm)) {
            tipo = "PIX_RECEBIDO";
            canal = CANAL_PIX;
            tipoTx = "RECEITA";
        } else if (isPixEnviado(norm)) {
            tipo = "PIX_ENVIADO";
            canal = CANAL_PIX;
        } else if (norm.contains("saque")) {
            tipo = "SAQUE";
            canal = CANAL_DEBITO;
        } else if (norm.contains("debito") || norm.contains("débito")) {
            tipo = "COMPRA_DEBITO";
            canal = CANAL_DEBITO;
        } else if (norm.contains("credito") || norm.contains("crédito") || norm.contains("cartao")
            || norm.contains("cartão") || "compra aprovada".equals(normalize(titulo == null ? "" : titulo))) {
            tipo = "COMPRA_CREDITO";
            canal = CANAL_CREDITO;
        } else if ("nubank".equals(banco) && (norm.contains("compra aprovada") || norm.contains("compra de"))) {
            tipo = "COMPRA_CREDITO";
            canal = CANAL_CREDITO;
        } else if ("itau".equals(banco) && norm.contains("compra")) {
            tipo = "COMPRA_CREDITO";
            canal = CANAL_CREDITO;
        } else {
            tipo = "COMPRA_DEBITO";
            canal = CANAL_DEBITO;
        }

        String merchant = extractMerchant(combined);
        if ((merchant == null || merchant.isBlank()) && "SAQUE".equals(tipo)) {
            merchant = "Saque";
        }
        if ((merchant == null || merchant.isBlank()) && tipo.startsWith("PIX")) {
            merchant = extractPixCounterparty(combined).orElse("PIX");
        }
        boolean confiante = merchant != null && !merchant.isBlank() && merchant.length() >= 2;
        if (!confiante) {
            return ParsedNotificacaoBancaria.naoReconhecida("baixa_confianca");
        }
        return ParsedNotificacaoBancaria.lancar(
            valorOpt.get(),
            merchant,
            MobileMoneyParser.normalizeMerchant(merchant),
            tipo,
            canal,
            tipoTx,
            true
        );
    }

    private static boolean isPixRecebido(String norm) {
        return (norm.contains("pix") && (norm.contains("recebeu") || norm.contains("recebido")
            || norm.contains("entrou") || norm.contains("creditado")))
            || norm.contains("voce recebeu um pix")
            || norm.contains("você recebeu um pix");
    }

    private static boolean isPixEnviado(String norm) {
        return norm.contains("pix") && (norm.contains("enviou") || norm.contains("enviado")
            || norm.contains("transferiu") || norm.contains("voce fez um pix")
            || norm.contains("você fez um pix") || norm.contains("pix de"));
    }

    private static String inferCanal(String norm, String banco) {
        if (norm.contains("pix")) {
            return CANAL_PIX;
        }
        if (norm.contains("debito") || norm.contains("débito") || norm.contains("saque")) {
            return CANAL_DEBITO;
        }
        if ("nubank".equals(banco) || "itau".equals(banco)) {
            return CANAL_CREDITO;
        }
        return CANAL_DEBITO;
    }

    static String extractMerchant(String combined) {
        if (combined == null || combined.isBlank()) {
            return null;
        }
        String trimmed = combined.trim();
        String found = firstGroup(MERCHANT_EM, trimmed);
        if (found == null) {
            found = firstGroup(MERCHANT_PARA, trimmed);
        }
        if (found == null) {
            found = firstGroup(MERCHANT_DE, trimmed);
        }
        if (found == null) {
            return null;
        }
        String cleaned = found.replaceAll("(?i)\\s+no\\s+cart[aã]o.*$", "")
            .replaceAll("(?i)\\s+final\\s+\\d{4}.*$", "")
            .replaceAll("[.!?]+$", "")
            .trim();
        if (cleaned.length() < 2) {
            return null;
        }
        return MobileMoneyParser.normalizeMerchant(cleaned);
    }

    private static String firstGroup(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static Optional<String> extractPixCounterparty(String combined) {
        Matcher m = Pattern.compile("(?i)(?:para|de)\\s+(.{2,80})$").matcher(combined.trim());
        if (m.find()) {
            return Optional.ofNullable(MobileMoneyParser.normalizeMerchant(m.group(1)));
        }
        return Optional.empty();
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .trim();
    }

    private static String join(String a, String b) {
        if (a == null || a.isBlank()) {
            return b == null ? "" : b.trim();
        }
        if (b == null || b.isBlank()) {
            return a.trim();
        }
        return a.trim() + " " + b.trim();
    }
}
