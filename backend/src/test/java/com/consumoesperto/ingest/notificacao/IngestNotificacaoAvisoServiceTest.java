package com.consumoesperto.ingest.notificacao;

import com.consumoesperto.model.IngestPreferencia;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngestNotificacaoAvisoServiceTest {

    @Test
    void cruzaMeiaNoite_silenciaMadrugadaENoite() {
        IngestPreferencia p = new IngestPreferencia();
        p.setSilenciosoInicio(LocalTime.of(22, 0));
        p.setSilenciosoFim(LocalTime.of(7, 0));
        assertTrue(IngestNotificacaoAvisoService.emHorarioSilencioso(p, LocalTime.of(23, 15)));
        assertTrue(IngestNotificacaoAvisoService.emHorarioSilencioso(p, LocalTime.of(3, 0)));
        assertFalse(IngestNotificacaoAvisoService.emHorarioSilencioso(p, LocalTime.of(12, 0)));
        assertFalse(IngestNotificacaoAvisoService.emHorarioSilencioso(p, LocalTime.of(7, 0)));
    }

    @Test
    void intervaloDiurno_soSilenciaDentro() {
        IngestPreferencia p = new IngestPreferencia();
        p.setSilenciosoInicio(LocalTime.of(12, 0));
        p.setSilenciosoFim(LocalTime.of(13, 0));
        assertTrue(IngestNotificacaoAvisoService.emHorarioSilencioso(p, LocalTime.of(12, 30)));
        assertFalse(IngestNotificacaoAvisoService.emHorarioSilencioso(p, LocalTime.of(13, 0)));
        assertFalse(IngestNotificacaoAvisoService.emHorarioSilencioso(p, LocalTime.of(11, 59)));
    }
}
