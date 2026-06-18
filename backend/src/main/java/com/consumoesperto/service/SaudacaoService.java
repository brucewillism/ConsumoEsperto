package com.consumoesperto.service;

import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * Detecção local de cumprimentos e respostas sensíveis ao horário (Brasil).
 */
@Service
public class SaudacaoService {

    private static final ZoneId FUSO_BRASIL = ZoneId.of("America/Sao_Paulo");

    private static final Pattern PADRAO_SAUDACAO = Pattern.compile(
        "^(?:"
            + "oi+"
            + "|ola+"
            + "|oie+"
            + "|alo+"
            + "|opa+"
            + "|e\\s*ai+"
            + "|eae+"
            + "|iae+"
            + "|iai+"
            + "|salve"
            + "|hey+"
            + "|hi+"
            + "|hello"
            + "|fala(?:\\s*(?:ai|jarvis|ae))?"
            + "|blz"
            + "|beleza"
            + "|de\\s*boa"
            + "|suave"
            + "|tudo\\s*(?:bem|bom|certo)"
            + "|como\\s*(?:vai|voce\\s*esta)"
            + "|bom\\s*dia"
            + "|boa\\s*tarde"
            + "|boa\\s*noite"
            + "|boa\\s*madrugada"
            + ")[\\s\\d\\p{Punct}]*$",
        Pattern.CASE_INSENSITIVE
    );

    private static final List<String> CORPOS = List.of(
        "Todos os sistemas operando normalmente. Como posso ajudar com as suas finanças hoje? 💼",
        "Tudo pronto por aqui. Quer lançar algum gasto ou dar uma olhada nos seus orçamentos? 📊",
        "J.A.R.V.I.S. à disposição. O painel está atualizado e o Sentinela de prontidão. Qual é a missão?",
        "Espero que o dia esteja produtivo. Como estão os planos para fechar o mês no verde? 🟢",
        "Por aqui está tudo sob controle. Posso te mostrar o resumo do mês, se quiser.",
        "Pronto para o que precisar — lançar uma despesa, conferir cartões ou planejar uma meta."
    );

    private final TextMatcherService textMatcherService;
    private final JarvisContextoService contextoService;

    public SaudacaoService(TextMatcherService textMatcherService, JarvisContextoService contextoService) {
        this.textMatcherService = textMatcherService;
        this.contextoService = contextoService;
    }

    public String normalizarParaDetecao(String mensagem) {
        if (mensagem == null || mensagem.isBlank()) {
            return "";
        }
        return textMatcherService.normalizarTexto(mensagem).replaceAll("\\s+", " ").trim();
    }

    public boolean isSaudacaoIsolada(String mensagem) {
        String norm = normalizarParaDetecao(mensagem);
        if (norm.isBlank()) {
            return false;
        }
        return PADRAO_SAUDACAO.matcher(norm).matches();
    }

    public String extrairSaudacaoUsada(String textoNormalizado) {
        return textoNormalizado != null ? textoNormalizado : "";
    }

    public String gerarResposta(String saudacaoUsada, Long usuarioId) {
        String periodo = determinarPeriodo(saudacaoUsada);
        String saudacaoInicial = montarSaudacaoInicial(periodo);
        String corpo = sortearCorpo(usuarioId);
        return saudacaoInicial + " " + corpo;
    }

    public String lembreteTutorialAtivo() {
        return "\n\n_(Você ainda está no guia. Digite um número de 1 a 5 ou *sair*.)_";
    }

    private String determinarPeriodo(String saudacaoUsada) {
        String periodoUsuario = null;
        if (saudacaoUsada != null) {
            if (saudacaoUsada.contains("bom dia")) {
                periodoUsuario = "MANHA";
            } else if (saudacaoUsada.contains("boa tarde")) {
                periodoUsuario = "TARDE";
            } else if (saudacaoUsada.contains("boa noite")) {
                periodoUsuario = "NOITE";
            } else if (saudacaoUsada.contains("madrugada")) {
                periodoUsuario = "MADRUGADA";
            }
        }

        String periodoRelogio = periodoDoRelogio();
        if (periodoUsuario != null && periodoUsuario.equals(periodoRelogio)) {
            return periodoUsuario;
        }
        return periodoRelogio;
    }

    private static String periodoDoRelogio() {
        int hora = LocalTime.now(FUSO_BRASIL).getHour();
        if (hora >= 5 && hora < 12) {
            return "MANHA";
        }
        if (hora >= 12 && hora < 18) {
            return "TARDE";
        }
        if (hora >= 18 && hora < 24) {
            return "NOITE";
        }
        return "MADRUGADA";
    }

    private static String montarSaudacaoInicial(String periodo) {
        return switch (periodo) {
            case "MANHA" -> "Bom dia, chefe!";
            case "TARDE" -> "Boa tarde, chefe!";
            case "NOITE" -> "Boa noite, chefe!";
            case "MADRUGADA" -> "Boa madrugada, chefe — trabalhando até tarde, hein?";
            default -> "Olá, chefe!";
        };
    }

    private String sortearCorpo(Long usuarioId) {
        JarvisContextoService.JarvisContexto contexto = contextoService.obter(usuarioId);
        int ultimo = contexto.getUltimoIndiceSaudacao();
        int novo;
        do {
            novo = ThreadLocalRandom.current().nextInt(CORPOS.size());
        } while (novo == ultimo && CORPOS.size() > 1);
        contexto.setUltimoIndiceSaudacao(novo);
        return CORPOS.get(novo);
    }
}
