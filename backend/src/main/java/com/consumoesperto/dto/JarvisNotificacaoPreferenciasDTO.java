package com.consumoesperto.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Preferências de notificações proativas J.A.R.V.I.S. (WhatsApp).
 * {@code null} em qualquer campo na gravação = manter valor actual.
 */
@Getter
@Setter
@NoArgsConstructor
public class JarvisNotificacaoPreferenciasDTO {

    /** Alerta após despesa (Sentinela + forecast + juros). */
    private Boolean alertaRiscoReativo;
    /** Domingo 18h — revisão semanal. */
    private Boolean resumoSemanal;
    /** Dia 1 18h30 — score e resultado do mês anterior. */
    private Boolean relatorioMensalScore;
    /** Dia 1 18h15 — digest Sentinela + projeção de fechamento. */
    private Boolean digestMensalSentinela;
    /** Dia 5 09h30 — disponibilidade real. */
    private Boolean sentinelaDia5;
    /** Diário 08h — recorrências, assinaturas, liquidez. */
    private Boolean recorrenciasVencimento;
    /** Segunda 10h — oportunidade debt snowball. */
    private Boolean amortizacaoSazonal;
    /** Diário 10h — lançamentos pendentes de conferência. */
    private Boolean conferenciaNotas;
    /** Segunda 10h — sugestão Modo Viagem (Cronos). */
    private Boolean modoViagemCronos;

    /** Canal de entrega: WHATSAPP, WEB ou AMBOS. */
    private String canalEntrega;

    public static JarvisNotificacaoPreferenciasDTO defaults() {
        JarvisNotificacaoPreferenciasDTO d = new JarvisNotificacaoPreferenciasDTO();
        d.setAlertaRiscoReativo(true);
        d.setResumoSemanal(true);
        d.setRelatorioMensalScore(true);
        d.setDigestMensalSentinela(true);
        d.setSentinelaDia5(true);
        d.setRecorrenciasVencimento(true);
        d.setAmortizacaoSazonal(true);
        d.setConferenciaNotas(true);
        d.setModoViagemCronos(true);
        d.setCanalEntrega("WHATSAPP");
        return d;
    }
}
