package com.consumoesperto.service;

import com.consumoesperto.dto.NotificacaoCelularWebhookRequest;
import com.consumoesperto.dto.NotificacaoCelularWebhookResponse;
import com.consumoesperto.dto.NotificacaoParseadaDTO;
import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.repository.ContaBancariaRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.util.BancoBrasilCatalog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class NotificacaoCelularWebhookService {

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    private static final long JANELA_DEDUPLICACAO_MS = 30_000L;

    private final NotificationParserService notificationParserService;
    private final ContaBancariaService contaBancariaService;
    private final ContaBancariaRepository contaBancariaRepository;
    private final TransacaoService transacaoService;
    private final SaldoService saldoService;
    private final WhatsAppNotificationService whatsAppNotificationService;
    private final JarvisProtocolService jarvisProtocolService;
    private final UsuarioRepository usuarioRepository;

    private final Map<String, Long> ultimosLancamentos = new ConcurrentHashMap<>();

    public NotificacaoCelularWebhookService(
        NotificationParserService notificationParserService,
        ContaBancariaService contaBancariaService,
        ContaBancariaRepository contaBancariaRepository,
        TransacaoService transacaoService,
        SaldoService saldoService,
        @Lazy WhatsAppNotificationService whatsAppNotificationService,
        JarvisProtocolService jarvisProtocolService,
        UsuarioRepository usuarioRepository
    ) {
        this.notificationParserService = notificationParserService;
        this.contaBancariaService = contaBancariaService;
        this.contaBancariaRepository = contaBancariaRepository;
        this.transacaoService = transacaoService;
        this.saldoService = saldoService;
        this.whatsAppNotificationService = whatsAppNotificationService;
        this.jarvisProtocolService = jarvisProtocolService;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public NotificacaoCelularWebhookResponse processar(Long usuarioId, NotificacaoCelularWebhookRequest request) {
        NotificacaoParseadaDTO parse = notificationParserService.parse(
            usuarioId,
            request.getTexto(),
            request.getApp()
        );

        if (!parse.isFinanceira() || parse.getValor().compareTo(BigDecimal.ZERO) <= 0) {
            return NotificacaoCelularWebhookResponse.builder()
                .status(NotificacaoCelularWebhookResponse.Status.IGNORADA)
                .mensagem("Notificação não financeira ou sem valor — ignorada.")
                .build();
        }

        if (ehDuplicataRecente(usuarioId, parse)) {
            log.info("[NOTIF-CELULAR] Duplicata ignorada userId={} banco={} valor={}", usuarioId, parse.getBanco(), parse.getValor());
            return NotificacaoCelularWebhookResponse.builder()
                .status(NotificacaoCelularWebhookResponse.Status.DUPLICADA)
                .mensagem("Lançamento duplicado nos últimos 30 segundos — ignorado.")
                .banco(BancoBrasilCatalog.nomeExibicao(parse.getBanco()))
                .build();
        }

        ContaBancaria conta = resolverContaPorBanco(usuarioId, parse.getBanco());
        if (conta == null) {
            throw new IllegalArgumentException(
                "Nenhuma conta ativa encontrada para o banco " + BancoBrasilCatalog.nomeExibicao(parse.getBanco())
                    + ". Cadastre a conta em Contas com nome compatível.");
        }

        TransacaoDTO.TipoTransacao tipo = parse.getTipo() == NotificacaoParseadaDTO.TipoMovimento.CREDITO
            ? TransacaoDTO.TipoTransacao.RECEITA
            : TransacaoDTO.TipoTransacao.DESPESA;

        TransacaoDTO tx = new TransacaoDTO();
        String descricao = parse.getDescricao();
        tx.setDescricao(descricao.length() > 200 ? descricao.substring(0, 200) : descricao);
        tx.setValor(parse.getValor());
        tx.setTipoTransacao(tipo);
        tx.setDataTransacao(parseDataEnvio(request.getDataEnvio()));
        tx.setStatusConferencia(TransacaoDTO.StatusConferencia.CONFIRMADA);
        tx.setContaBancariaId(conta.getId());

        TransacaoDTO criada = transacaoService.criarTransacao(tx, usuarioId, false, false);
        registrarLancamento(usuarioId, parse);

        ContaBancaria contaAtualizada = contaBancariaRepository.findById(conta.getId()).orElse(conta);
        BigDecimal saldoAtual = contaAtualizada.getSaldoAtual();
        saldoService.notificarAlteracaoSaldo(usuarioId);

        enviarFeedbackWhatsapp(usuarioId, parse, contaAtualizada.getNome(), saldoAtual);

        log.info("[NOTIF-CELULAR] Lançamento userId={} transacaoId={} banco={} tipo={} valor={} saldo={}",
            usuarioId, criada.getId(), parse.getBanco(), parse.getTipo(), parse.getValor(), saldoAtual);

        return NotificacaoCelularWebhookResponse.builder()
            .status(NotificacaoCelularWebhookResponse.Status.PROCESSADA)
            .mensagem("Transação registrada e saldo atualizado.")
            .transacaoId(criada.getId())
            .banco(BancoBrasilCatalog.nomeExibicao(parse.getBanco()))
            .contaNome(contaAtualizada.getNome())
            .saldoAtualConta(saldoAtual)
            .build();
    }

    private ContaBancaria resolverContaPorBanco(Long usuarioId, String bancoRef) {
        if (bancoRef == null || bancoRef.isBlank()) {
            return contaBancariaService.resolverContaParaTransacao(usuarioId, null);
        }
        List<ContaBancaria> todas = contaBancariaRepository.findByUsuarioIdAndAtivaTrueOrderByPadraoDescNomeAsc(usuarioId);
        List<ContaBancaria> porCatalogo = todas.stream()
            .filter(c -> BancoBrasilCatalog.bancosCorrespondem(c.getNome(), bancoRef))
            .sorted(Comparator.comparing(ContaBancaria::isPadrao).reversed()
                .thenComparing(c -> c.getNome() != null ? c.getNome() : ""))
            .toList();
        if (!porCatalogo.isEmpty()) {
            return porCatalogo.get(0);
        }
        List<ContaBancaria> porApelido = contaBancariaService.encontrarAtivasPorApelidoNormalizado(usuarioId, bancoRef);
        if (!porApelido.isEmpty()) {
            return porApelido.get(0);
        }
        return contaBancariaService.resolverContaParaTransacao(usuarioId, null);
    }

    private boolean ehDuplicataRecente(Long usuarioId, NotificacaoParseadaDTO parse) {
        String chave = chaveDeduplicacao(usuarioId, parse);
        long agora = System.currentTimeMillis();
        limparEntradasAntigas(agora);
        Long anterior = ultimosLancamentos.get(chave);
        return anterior != null && (agora - anterior) < JANELA_DEDUPLICACAO_MS;
    }

    private void registrarLancamento(Long usuarioId, NotificacaoParseadaDTO parse) {
        ultimosLancamentos.put(chaveDeduplicacao(usuarioId, parse), System.currentTimeMillis());
    }

    private static String chaveDeduplicacao(Long usuarioId, NotificacaoParseadaDTO parse) {
        String desc = parse.getDescricao() != null
            ? parse.getDescricao().toLowerCase(Locale.ROOT).trim()
            : "";
        return usuarioId + "|" + parse.getBanco() + "|" + parse.getValor().toPlainString() + "|" + desc;
    }

    private void limparEntradasAntigas(long agora) {
        if (ultimosLancamentos.size() < 200) {
            return;
        }
        ultimosLancamentos.entrySet().removeIf(e -> (agora - e.getValue()) > JANELA_DEDUPLICACAO_MS * 4);
    }

    private void enviarFeedbackWhatsapp(
        Long usuarioId,
        NotificacaoParseadaDTO parse,
        String contaNome,
        BigDecimal saldoAtual
    ) {
        try {
            String vocativo = jarvisProtocolService.resolveVocative(usuarioId, usuarioRepository);
            String banco = BancoBrasilCatalog.nomeExibicao(parse.getBanco());
            String msg;
            if (parse.getTipo() == NotificacaoParseadaDTO.TipoMovimento.CREDITO) {
                msg = vocativo + ", peguei a notificação aqui! Acabei de lançar um crédito de "
                    + BRL.format(parse.getValor()) + " (" + parse.getDescricao() + ") na sua conta "
                    + banco + ". Seu novo saldo lá é " + BRL.format(saldoAtual) + ".";
            } else {
                msg = vocativo + ", peguei a notificação aqui! Acabei de lançar um débito de "
                    + BRL.format(parse.getValor()) + " (" + parse.getDescricao() + ") na sua conta "
                    + banco + ". Seu novo saldo lá é " + BRL.format(saldoAtual) + ".";
            }
            whatsAppNotificationService.enviarParaUsuario(usuarioId, msg);
        } catch (Exception e) {
            log.warn("[NOTIF-CELULAR] Falha ao enviar WhatsApp userId={}: {}", usuarioId, e.getMessage());
        }
    }

    private static LocalDateTime parseDataEnvio(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(raw.trim(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(raw.trim(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            } catch (Exception e2) {
                return LocalDateTime.now();
            }
        }
    }
}
