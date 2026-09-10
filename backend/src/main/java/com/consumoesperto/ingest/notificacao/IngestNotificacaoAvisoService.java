package com.consumoesperto.ingest.notificacao;

import com.consumoesperto.ingest.notificacao.parser.ParsedNotificacaoBancaria;
import com.consumoesperto.model.IngestPreferencia;
import com.consumoesperto.model.NotificacaoBancariaRecebida;
import com.consumoesperto.repository.NotificacaoBancariaRecebidaRepository;
import com.consumoesperto.service.UsuarioSessaoContextoService;
import com.consumoesperto.service.WhatsAppNotificationService;
import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.service.TransacaoService;
import com.consumoesperto.util.AppTimeZone;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngestNotificacaoAvisoService {

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    private final NotificacaoBancariaRecebidaRepository recebidaRepository;
    private final WhatsAppNotificationService whatsAppNotificationService;
    private final UsuarioSessaoContextoService sessaoContextoService;
    private final TransacaoService transacaoService;
    private final IngestPreferenciaService preferenciaService;

    @Transactional
    public void agendarOuEnviar(
        Long usuarioId,
        Long notificacaoId,
        Long transacaoId,
        ParsedNotificacaoBancaria parsed,
        IngestNotificacaoProcessor.RecursoAlvo alvo,
        IngestPreferencia pref
    ) {
        NotificacaoBancariaRecebida row = recebidaRepository.findById(notificacaoId).orElse(null);
        if (row == null) {
            return;
        }
        boolean resumo = IngestPreferencia.AGRUPAMENTO_RESUMO.equalsIgnoreCase(pref.getAgrupamento());
        if (resumo || emHorarioSilencioso(pref)) {
            row.setAvisoPendente(true);
            recebidaRepository.save(row);
            return;
        }
        enviarUm(usuarioId, row, transacaoId, parsed, alvo);
    }

    @Transactional
    public int despejarPendentes() {
        List<NotificacaoBancariaRecebida> pendentes = recebidaRepository.findByAvisoPendenteTrue();
        Map<Long, List<NotificacaoBancariaRecebida>> porUsuario = pendentes.stream()
            .collect(Collectors.groupingBy(NotificacaoBancariaRecebida::getUsuarioId));
        int enviados = 0;
        for (Map.Entry<Long, List<NotificacaoBancariaRecebida>> e : porUsuario.entrySet()) {
            Long usuarioId = e.getKey();
            IngestPreferencia pref = preferenciaService.obterOuCriar(usuarioId);
            if (emHorarioSilencioso(pref)) {
                continue;
            }
            List<NotificacaoBancariaRecebida> lista = e.getValue().stream()
                .sorted(Comparator.comparing(NotificacaoBancariaRecebida::getCriadoEm))
                .toList();
            if (IngestPreferencia.AGRUPAMENTO_RESUMO.equalsIgnoreCase(pref.getAgrupamento())) {
                LocalDateTime maisAntiga = lista.get(0).getCriadoEm();
                if (maisAntiga.plusMinutes(pref.getResumoMinutos()).isAfter(AppTimeZone.agora())) {
                    continue;
                }
                enviarResumo(usuarioId, lista);
                enviados++;
            } else {
                for (NotificacaoBancariaRecebida row : lista) {
                    enviarUm(usuarioId, row, row.getTransacaoId(), null, null);
                    enviados++;
                }
            }
        }
        return enviados;
    }

    private void enviarUm(
        Long usuarioId,
        NotificacaoBancariaRecebida row,
        Long transacaoId,
        ParsedNotificacaoBancaria parsed,
        IngestNotificacaoProcessor.RecursoAlvo alvo
    ) {
        if (transacaoId == null) {
            marcarEnviado(row);
            return;
        }
        TransacaoDTO tx;
        try {
            tx = transacaoService.buscarPorId(transacaoId, usuarioId);
        } catch (Exception e) {
            marcarEnviado(row);
            return;
        }
        String valor = BRL.format(tx.getValor());
        String cat = tx.getCategoriaNome() != null ? tx.getCategoriaNome() : "sem categoria";
        String destino = destinoTexto(tx, alvo);
        String padraoHint = alvo != null && alvo.usouContaPadrao()
            ? " Usei a conta padrão — associe o app do banco em Captura automática se quiser mudar."
            : "";
        String msg = "Registrei sozinho: *" + valor + "* em *" + tx.getDescricao() + "* (" + cat + ") "
            + destino + "." + padraoHint
            + "\n\nSe não for isso, responda *apagar*. "
            + "Para mudar a categoria: *categoria Alimentação*. "
            + "Para mudar a conta: *conta Nubank* ou *cartão Itaú*.";
        whatsAppNotificationService.enviarParaUsuario(usuarioId, msg);
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("transacaoId", transacaoId);
        ctx.put("notificacaoId", row.getId());
        sessaoContextoService.salvar(
            usuarioId,
            UsuarioSessaoContextoService.CANAL_WHATSAPP,
            UsuarioSessaoContextoService.CHAVE_CAPTURA_NOTIFICACAO,
            ctx,
            24 * 60
        );
        marcarEnviado(row);
    }

    private void enviarResumo(Long usuarioId, List<NotificacaoBancariaRecebida> lista) {
        StringBuilder sb = new StringBuilder("Resumo da captura automática:\n");
        Long ultimoTx = null;
        for (NotificacaoBancariaRecebida row : lista) {
            if (row.getTransacaoId() == null) {
                marcarEnviado(row);
                continue;
            }
            try {
                TransacaoDTO tx = transacaoService.buscarPorId(row.getTransacaoId(), usuarioId);
                sb.append("• ").append(BRL.format(tx.getValor())).append(" em ").append(tx.getDescricao());
                if (tx.getCategoriaNome() != null) {
                    sb.append(" (").append(tx.getCategoriaNome()).append(")");
                }
                sb.append("\n");
                ultimoTx = tx.getId();
            } catch (Exception ignored) {
                // transação já apagada
            }
            marcarEnviado(row);
        }
        sb.append("\nPara corrigir o último lançamento, responda *apagar*, *categoria …* ou *conta …*.");
        whatsAppNotificationService.enviarParaUsuario(usuarioId, sb.toString());
        if (ultimoTx != null) {
            Map<String, Object> ctx = new HashMap<>();
            ctx.put("transacaoId", ultimoTx);
            sessaoContextoService.salvar(
                usuarioId,
                UsuarioSessaoContextoService.CANAL_WHATSAPP,
                UsuarioSessaoContextoService.CHAVE_CAPTURA_NOTIFICACAO,
                ctx,
                24 * 60
            );
        }
    }

    private void marcarEnviado(NotificacaoBancariaRecebida row) {
        row.setAvisoPendente(false);
        recebidaRepository.save(row);
    }

    private static String destinoTexto(TransacaoDTO tx, IngestNotificacaoProcessor.RecursoAlvo alvo) {
        if (tx.getCartaoCreditoNome() != null && !tx.getCartaoCreditoNome().isBlank()) {
            return "no cartão " + tx.getCartaoCreditoNome();
        }
        if (tx.getContaBancariaNome() != null && !tx.getContaBancariaNome().isBlank()) {
            return "na conta " + tx.getContaBancariaNome();
        }
        return "no seu extrato";
    }

    static boolean emHorarioSilencioso(IngestPreferencia pref) {
        LocalTime inicio = pref.getSilenciosoInicio();
        LocalTime fim = pref.getSilenciosoFim();
        if (inicio == null || fim == null) {
            return false;
        }
        LocalTime agora = AppTimeZone.agora().toLocalTime();
        return emHorarioSilencioso(pref, agora);
    }

    static boolean emHorarioSilencioso(IngestPreferencia pref, LocalTime agora) {
        LocalTime inicio = pref.getSilenciosoInicio();
        LocalTime fim = pref.getSilenciosoFim();
        if (inicio == null || fim == null || agora == null) {
            return false;
        }
        if (inicio.equals(fim)) {
            return false;
        }
        if (inicio.isBefore(fim)) {
            return !agora.isBefore(inicio) && agora.isBefore(fim);
        }
        return !agora.isBefore(inicio) || agora.isBefore(fim);
    }
}
