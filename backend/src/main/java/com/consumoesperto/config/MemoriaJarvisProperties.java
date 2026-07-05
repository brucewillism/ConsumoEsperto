package com.consumoesperto.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Memória semântica J.A.R.V.I.S. — higiene de escrita, recuperação (RAG) e ciclo de vida.
 */
@Data
@Component
@ConfigurationProperties(prefix = "consumoesperto.memoria")
public class MemoriaJarvisProperties {

    /** Similaridade mínima (0–1) para considerar duas memórias «quase idênticas» e reforçar em vez de duplicar. */
    private double dedupeSimilaridadeMinima = 0.90;

    /** Similaridade mínima (0–1) para memória nova SUPERAR uma antiga do mesmo tipo (contradição; a recente vence). */
    private double superacaoSimilaridadeMinima = 0.78;

    /** Máximo de memórias injetadas por conversa/consulta RAG. */
    private int ragLimiteContexto = 5;

    /** Meia-vida (dias) do peso de recência no score híbrido. */
    private int ragMeiaVidaDias = 90;

    /** Ocorrências mínimas para declarar um hábito (efeito dominó). */
    private int habitoMinOcorrencias = 5;

    /** Dias distintos mínimos entre as ocorrências do hábito. */
    private int habitoMinDiasDistintos = 3;

    /** Par com mesma descrição em menos de N minutos = suspeita de duplicata, não conta como hábito. */
    private int habitoJanelaMinutosDuplicata = 30;

    /** Quanto a confiança de memórias INFERIDO decai por mês sem reforço. */
    private BigDecimal decaimentoConfiancaMensal = new BigDecimal("0.05");

    /** Piso de confiança; abaixo disso a memória é ARQUIVADA pelo job de ciclo de vida. */
    private BigDecimal confiancaPiso = new BigDecimal("0.25");

    /** Quantidade mínima de memórias do mesmo tema para o job de consolidação gerar um resumo. */
    private int consolidacaoMinCluster = 6;

    /** Similaridade mínima (0–1) para duas memórias pertencerem ao mesmo cluster de consolidação. */
    private double consolidacaoSimilaridade = 0.85;

    /** Habilita a extração automática de memórias das conversas (sem «anote isso»). */
    private boolean capturaAutomaticaEnabled = true;
}
