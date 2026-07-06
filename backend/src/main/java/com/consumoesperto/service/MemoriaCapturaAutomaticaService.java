package com.consumoesperto.service;

import com.consumoesperto.config.MemoriaJarvisProperties;
import com.consumoesperto.model.MemoriaCategoriaOrigem;
import com.consumoesperto.model.MemoriaMetadados;
import com.consumoesperto.model.MemoriaTipo;
import com.consumoesperto.model.OrigemConteudo;
import com.consumoesperto.util.AppTimeZone;
import com.consumoesperto.util.MemoriaTextoHeuristica;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Captura automática de memórias (Bloco 3.1): extrai fatos/planos/preferências do que o usuário
 * DIGITOU ou FALOU nas conversas, sem exigir «Jarvis, anote isso».
 *
 * <p>Fontes: campo {@code memoriasSugeridas[]} devolvido pela MESMA chamada de LLM do parse de
 * comando (sem chamada extra) + heurística local de fallback. O guardrail anti-injection é
 * estrutural: a {@link OrigemConteudo} é obrigatória e qualquer origem que não seja texto/áudio
 * do usuário (ex.: DOCUMENTO) é recusada AQUI DENTRO, independentemente do chamador.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemoriaCapturaAutomaticaService {

    private static final int MAX_MEMORIAS_POR_MENSAGEM = 3;
    private static final int MAX_TAMANHO_TEXTO = 500;
    /** N falhas na última hora → alerta operacional (o cooldown fica no AlertaOperacionalService). */
    private static final int FALHAS_POR_HORA_PARA_ALERTA = 3;

    private final CerebroSemanticoService cerebroSemanticoService;
    private final MemoriaJarvisProperties memoriaProps;
    private final AlertaOperacionalService alertaOperacionalService;

    private final ConcurrentLinkedDeque<Instant> falhasRecentes = new ConcurrentLinkedDeque<>();

    /**
     * Processa a conversa fora do thread de resposta. {@code parsed} é o JSON já devolvido pelo
     * parse do comando (pode conter {@code memoriasSugeridas}); {@code textoUsuario} é o texto
     * digitado/falado pelo usuário. A {@code origem} é obrigatória — origens que não sejam
     * texto/áudio do usuário são recusadas (guardrail anti-injection estrutural).
     */
    @Async("cerebroExecutor")
    public void capturarDeConversaAsync(Long userId, String textoUsuario, JsonNode parsed, OrigemConteudo origem) {
        if (origem == null || !origem.podeGerarMemoriaAutomatica()) {
            log.warn("[MEMORIA] Captura automática RECUSADA por origem não permitida userId={} origem={}",
                userId, origem);
            return;
        }
        if (!memoriaProps.isCapturaAutomaticaEnabled() || userId == null
            || textoUsuario == null || textoUsuario.isBlank()) {
            return;
        }
        try {
            int gravadas = capturarDeSugestoesDaIa(userId, parsed);
            if (gravadas == 0) {
                capturarPorHeuristica(userId, textoUsuario);
            }
        } catch (Exception e) {
            registrarFalhaCaptura(userId, origem, e);
        }
    }

    /**
     * Falha no caminho async não pode ser engolida (item 3): log ERROR estruturado (sem o conteúdo
     * da mensagem, que pode ser sensível) e alerta operacional quando há falhas repetidas na hora.
     */
    private void registrarFalhaCaptura(Long userId, OrigemConteudo origem, Exception e) {
        log.error("[MEMORIA] Captura automática FALHOU userId={} origem={} causa={}: {}",
            userId, origem, e.getClass().getSimpleName(), e.getMessage(), e);
        Instant agora = Instant.now();
        falhasRecentes.addLast(agora);
        Instant corte = agora.minus(1, ChronoUnit.HOURS);
        falhasRecentes.removeIf(t -> t.isBefore(corte));
        if (falhasRecentes.size() >= FALHAS_POR_HORA_PARA_ALERTA) {
            alertaOperacionalService.alertar(
                AlertaOperacionalService.TIPO_MEMORIA_CAPTURA_FALHA,
                String.format("Captura automática de memória falhou %d vez(es) na última hora. "
                    + "Última falha: userId=%d causa=%s", falhasRecentes.size(), userId,
                    e.getClass().getSimpleName()));
        }
    }

    private int capturarDeSugestoesDaIa(Long userId, JsonNode parsed) {
        if (parsed == null) {
            return 0;
        }
        JsonNode arr = parsed.path("memoriasSugeridas");
        if (!arr.isArray() || arr.isEmpty()) {
            return 0;
        }
        LocalDate hoje = AppTimeZone.hoje();
        int gravadas = 0;
        for (JsonNode item : arr) {
            if (gravadas >= MAX_MEMORIAS_POR_MENSAGEM) {
                break;
            }
            String texto = item.path("texto").asText("").trim();
            if (texto.isBlank()) {
                continue;
            }
            MemoriaTipo tipo = tipoDeString(item.path("tipo").asText(""), texto, hoje);
            MemoriaMetadados meta = MemoriaMetadados.inferido(tipo);
            if (item.hasNonNull("valor") && item.path("valor").isNumber()) {
                meta = meta.comValor(BigDecimal.valueOf(item.path("valor").asDouble()));
            }
            int mesAlvo = item.path("mesAlvo").asInt(0);
            if (mesAlvo >= 1 && mesAlvo <= 12) {
                int anoAlvo = item.path("anoAlvo").asInt(0);
                int ano = anoAlvo >= 2000 ? anoAlvo : anoParaMesAlvo(mesAlvo, hoje);
                meta = meta.comAlvo(mesAlvo, ano);
                if (tipo == MemoriaTipo.PLANO_FUTURO) {
                    meta = meta.comValidade(LocalDate.of(ano, mesAlvo, 1).plusMonths(1));
                }
            }
            meta = MemoriaTextoHeuristica.enriquecer(meta, texto, hoje);
            cerebroSemanticoService.gravarMemoria(
                userId, truncar(texto), MemoriaCategoriaOrigem.FINANCAS, meta);
            gravadas++;
        }
        return gravadas;
    }

    /** Fallback sem IA: só grava quando o texto tem cara inequívoca de plano futuro ou preferência. */
    private void capturarPorHeuristica(Long userId, String textoUsuario) {
        LocalDate hoje = AppTimeZone.hoje();
        MemoriaTipo tipo = MemoriaTextoHeuristica.detectarTipo(textoUsuario, hoje);
        if (tipo == MemoriaTipo.FATO) {
            return;
        }
        MemoriaMetadados meta = MemoriaTextoHeuristica.enriquecer(
            MemoriaMetadados.inferido(tipo), textoUsuario, hoje);
        if (tipo == MemoriaTipo.PLANO_FUTURO && meta.mesAlvo() == null) {
            return;
        }
        cerebroSemanticoService.gravarMemoria(
            userId, truncar(textoUsuario.trim()), MemoriaCategoriaOrigem.FINANCAS, meta);
    }

    private static MemoriaTipo tipoDeString(String raw, String texto, LocalDate hoje) {
        try {
            MemoriaTipo t = MemoriaTipo.valueOf(raw.trim().toUpperCase());
            // A IA só pode sugerir tipos de conversa; tipos de sistema viram FATO
            if (t == MemoriaTipo.RESUMO_MENSAL || t == MemoriaTipo.EVENTO_SAZONAL) {
                return MemoriaTipo.FATO;
            }
            return t;
        } catch (Exception e) {
            return MemoriaTextoHeuristica.detectarTipo(texto, hoje);
        }
    }

    private static int anoParaMesAlvo(int mes, LocalDate hoje) {
        return mes < hoje.getMonthValue() ? hoje.getYear() + 1 : hoje.getYear();
    }

    private static String truncar(String s) {
        return s.length() > MAX_TAMANHO_TEXTO ? s.substring(0, MAX_TAMANHO_TEXTO - 1) + "…" : s;
    }
}
