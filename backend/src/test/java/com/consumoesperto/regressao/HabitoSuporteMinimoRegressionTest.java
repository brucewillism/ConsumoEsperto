package com.consumoesperto.regressao;

import com.consumoesperto.config.MemoriaJarvisProperties;
import com.consumoesperto.model.MemoriaCategoriaOrigem;
import com.consumoesperto.model.MemoriaMetadados;
import com.consumoesperto.model.MemoriaTipo;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.service.CerebroSemanticoService;
import com.consumoesperto.service.HabitDominoService;
import com.consumoesperto.service.JarvisProtocolService;
import com.consumoesperto.service.WhatsAppNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regressão do «hábito fantasma» (bug de transações duplicadas do Nubank):
 * 1.3 — hábito exige N mínimo de ocorrências em dias distintos; sequências com a MESMA
 * descrição em poucos minutos (assinatura de duplicata) não contam.
 */
@ExtendWith(MockitoExtension.class)
class HabitoSuporteMinimoRegressionTest {

    @Mock private TransacaoRepository transacaoRepository;
    @Mock private CerebroSemanticoService cerebroSemanticoService;
    @Mock private WhatsAppNotificationService whatsAppNotificationService;
    @Mock private JarvisProtocolService jarvisProtocolService;
    @Mock private UsuarioRepository usuarioRepository;

    private MemoriaJarvisProperties props;
    private HabitDominoService service;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        props = new MemoriaJarvisProperties();
        service = new HabitDominoService(
            transacaoRepository, cerebroSemanticoService, whatsAppNotificationService,
            jarvisProtocolService, usuarioRepository, props);
        usuario = new Usuario();
        usuario.setId(7L);
    }

    private Transacao despesa(long id, String descricao, LocalDateTime quando, String valor) {
        Transacao t = new Transacao();
        t.setId(id);
        t.setUsuario(usuario);
        t.setDescricao(descricao);
        t.setValor(new BigDecimal(valor));
        t.setTipoTransacao(Transacao.TipoTransacao.DESPESA);
        t.setStatusConferencia(Transacao.StatusConferencia.CONFIRMADA);
        t.setDataTransacao(quando);
        return t;
    }

    @Test
    void transacoesDuplicadasNoMesmoMinuto_naoViramHabito() {
        // Bug real: mesma descrição, mesmo minuto, 5 lançamentos — era contado como «observado 4×»
        LocalDateTime base = LocalDateTime.now().minusDays(2).withHour(14).withMinute(0);
        List<Transacao> historico = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            historico.add(despesa(100L + i, "gasto no cartao Nubank", base.plusSeconds(i * 30L), "259.90"));
        }
        when(transacaoRepository.findByUsuarioIdAndTipoTransacaoOrderByDataTransacaoDesc(
            eq(7L), eq(Transacao.TipoTransacao.DESPESA))).thenReturn(historico);

        service.avaliarEfeitoDominioPosDespesa(historico.get(historico.size() - 1));

        verify(cerebroSemanticoService, never())
            .gravarMemoria(anyLong(), anyString(), any(), any());
        verify(cerebroSemanticoService, never())
            .gravarMemoria(anyLong(), anyString(), any(MemoriaCategoriaOrigem.class));
    }

    @Test
    void poucasOcorrencias_abaixoDoSuporteMinimo_naoViramHabito() {
        // 4 pares em 4 dias distintos, mas o suporte mínimo default é 5
        List<Transacao> historico = new ArrayList<>();
        long id = 1;
        for (int dia = 0; dia < 4; dia++) {
            LocalDateTime d = LocalDateTime.now().minusDays(40L - dia * 7L).withHour(12).withMinute(0);
            historico.add(despesa(id++, "posto shell", d, "150.00"));
            historico.add(despesa(id++, "conveniencia am pm", d.plusHours(1), "40.00"));
        }
        Transacao gatilho = despesa(id++, "conveniencia am pm",
            historico.get(historico.size() - 1).getDataTransacao(), "40.00");
        historico.add(gatilho);
        when(transacaoRepository.findByUsuarioIdAndTipoTransacaoOrderByDataTransacaoDesc(
            eq(7L), eq(Transacao.TipoTransacao.DESPESA))).thenReturn(historico);

        service.avaliarEfeitoDominioPosDespesa(gatilho);

        verify(cerebroSemanticoService, never())
            .gravarMemoria(anyLong(), anyString(), any(), any());
    }

    @Test
    void padraoRealSemanal_viraHabitoComEvidencia() {
        // 6 pares posto → conveniência em 6 dias distintos (acima do suporte mínimo default 5/3)
        List<Transacao> historico = new ArrayList<>();
        long id = 1;
        for (int dia = 0; dia < 6; dia++) {
            LocalDateTime d = LocalDateTime.now().minusDays(60L - dia * 7L).withHour(12).withMinute(0);
            historico.add(despesa(id++, "posto shell", d, "150.00"));
            historico.add(despesa(id++, "conveniencia am pm", d.plusHours(1), "40.00"));
        }
        // Gatilho atual: nova conveniência 1h depois de um posto
        LocalDateTime agora = LocalDateTime.now().minusHours(1);
        Transacao posto = despesa(id++, "posto shell", agora.minusHours(1), "150.00");
        Transacao gatilho = despesa(id++, "conveniencia am pm", agora, "40.00");
        historico.add(posto);
        historico.add(gatilho);
        when(transacaoRepository.findByUsuarioIdAndTipoTransacaoOrderByDataTransacaoDesc(
            eq(7L), eq(Transacao.TipoTransacao.DESPESA))).thenReturn(historico);

        service.avaliarEfeitoDominioPosDespesa(gatilho);

        ArgumentCaptor<MemoriaMetadados> metaCap = ArgumentCaptor.forClass(MemoriaMetadados.class);
        ArgumentCaptor<String> ctxCap = ArgumentCaptor.forClass(String.class);
        verify(cerebroSemanticoService).gravarMemoria(
            eq(7L), ctxCap.capture(), eq(MemoriaCategoriaOrigem.HABITO), metaCap.capture());

        MemoriaMetadados meta = metaCap.getValue();
        assertEquals(MemoriaTipo.HABITO, meta.tipo());
        assertNotNull(meta.transacoesEvidencia(), "hábito deve guardar ids de evidência");
        assertFalse(meta.transacoesEvidencia().isEmpty());
        assertEquals(0, new BigDecimal("0.50").compareTo(meta.confianca()),
            "padrão inferido nasce com confiança média");
    }
}
