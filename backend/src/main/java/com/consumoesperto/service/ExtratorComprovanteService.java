package com.consumoesperto.service;

import com.consumoesperto.dto.ExtratorComprovanteWebhookRequest;
import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.model.Categoria;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.repository.CategoriaRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
public class ExtratorComprovanteService {

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    private static final int TTL_SESSAO_MINUTOS = 15;

    private final OpenAiService openAiService;
    private final ContaBancariaService contaBancariaService;
    private final TransacaoService transacaoService;
    private final UsuarioSessaoContextoService sessaoContextoService;
    private final WhatsAppNotificationService whatsAppNotificationService;
    private final UsuarioRepository usuarioRepository;
    private final JarvisProtocolService jarvisProtocolService;
    private final CategoriaRepository categoriaRepository;

    public ExtratorComprovanteService(
        OpenAiService openAiService,
        ContaBancariaService contaBancariaService,
        TransacaoService transacaoService,
        UsuarioSessaoContextoService sessaoContextoService,
        @Lazy WhatsAppNotificationService whatsAppNotificationService,
        UsuarioRepository usuarioRepository,
        JarvisProtocolService jarvisProtocolService,
        CategoriaRepository categoriaRepository
    ) {
        this.openAiService = openAiService;
        this.contaBancariaService = contaBancariaService;
        this.transacaoService = transacaoService;
        this.sessaoContextoService = sessaoContextoService;
        this.whatsAppNotificationService = whatsAppNotificationService;
        this.usuarioRepository = usuarioRepository;
        this.jarvisProtocolService = jarvisProtocolService;
        this.categoriaRepository = categoriaRepository;
    }

    @Transactional
    public TransacaoDTO processarWebhook(Long usuarioId, ExtratorComprovanteWebhookRequest request) {
        JsonNode json = openAiService.gerarJson(
            usuarioId,
            "Extraia dados de comprovante PIX/TED de notificação bancária. "
                + "Retorne JSON: {\"valor\":0.00,\"tipo\":\"RECEITA|DESPESA\",\"data\":\"yyyy-MM-dd\","
                + "\"descricao\":\"nome do recebedor ou pagador\","
                + "\"categoria\":\"categoria provável ex: Alimentação, Mercado, Transporte\","
                + "\"confianca\":0-1}. "
                + "tipo=RECEITA se dinheiro entrou na conta; DESPESA se saiu.",
            "Banco: " + request.getBanco() + "\nTexto da notificação:\n" + request.getTexto()
        );

        BigDecimal valor = BigDecimal.valueOf(json.path("valor").asDouble(0));
        if (valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Não foi possível extrair valor válido do comprovante.");
        }
        String tipoStr = json.path("tipo").asText("DESPESA").trim().toUpperCase();
        TransacaoDTO.TipoTransacao tipo = "RECEITA".equals(tipoStr)
            ? TransacaoDTO.TipoTransacao.RECEITA
            : TransacaoDTO.TipoTransacao.DESPESA;
        String descricao = json.path("descricao").asText("Comprovante " + request.getBanco()).trim();
        String categoriaNome = json.path("categoria").asText("Outros").trim();
        LocalDateTime data = parseData(json.path("data").asText(""));

        ContaBancaria conta = resolverConta(usuarioId, request.getBanco());
        Long categoriaId = resolverCategoriaId(usuarioId, categoriaNome, tipo);

        TransacaoDTO tx = new TransacaoDTO();
        tx.setDescricao(descricao.length() > 200 ? descricao.substring(0, 200) : descricao);
        tx.setValor(valor);
        tx.setTipoTransacao(tipo);
        tx.setDataTransacao(data);
        tx.setStatusConferencia(TransacaoDTO.StatusConferencia.PENDENTE);
        tx.setCategoriaId(categoriaId);
        if (conta != null) {
            tx.setContaBancariaId(conta.getId());
        }

        TransacaoDTO criada = transacaoService.criarTransacao(tx, usuarioId, false);

        Map<String, Object> contexto = new HashMap<>();
        contexto.put("transacaoId", criada.getId());
        contexto.put("valor", valor);
        contexto.put("categoriaNome", nomeCategoria(categoriaId, categoriaNome));
        contexto.put("banco", request.getBanco());
        contexto.put("descricao", criada.getDescricao());
        contexto.put("tipo", tipo.name());

        sessaoContextoService.salvar(
            usuarioId,
            UsuarioSessaoContextoService.CANAL_WHATSAPP,
            UsuarioSessaoContextoService.CHAVE_COMPROVANTE_CONFIRMACAO,
            contexto,
            TTL_SESSAO_MINUTOS
        );

        enviarConfirmacaoWhatsapp(usuarioId, contexto);

        log.info("[EXTRATOR] Comprovante pendente userId={} transacaoId={} banco={} valor={}",
            usuarioId, criada.getId(), request.getBanco(), valor);
        return criada;
    }

    private void enviarConfirmacaoWhatsapp(Long usuarioId, Map<String, Object> contexto) {
        String vocativo = jarvisProtocolService.resolveVocative(usuarioId, usuarioRepository);
        String categoria = String.valueOf(contexto.getOrDefault("categoriaNome", "Outros"));
        String banco = String.valueOf(contexto.getOrDefault("banco", "conta"));
        BigDecimal valor = contexto.get("valor") instanceof BigDecimal b
            ? b
            : BigDecimal.valueOf(((Number) contexto.get("valor")).doubleValue());
        String tipo = String.valueOf(contexto.getOrDefault("tipo", "DESPESA"));

        String msg;
        if ("RECEITA".equals(tipo)) {
            msg = vocativo + ", registrei uma *receita* de " + BRL.format(valor)
                + " em *" + categoria + "* no " + banco + ". Confirma?";
        } else {
            msg = vocativo + ", registrei um gasto de " + BRL.format(valor)
                + " em *" + categoria + "* no " + banco + ". Confirma?";
        }
        msg += "\n\nResponda *sim* para confirmar, *não* para cancelar, ou diga o que alterar "
            + "(ex.: _Não, altera para Mercado_).";

        whatsAppNotificationService.enviarParaUsuario(usuarioId, msg);
    }

    private Long resolverCategoriaId(Long usuarioId, String categoriaNome, TransacaoDTO.TipoTransacao tipo) {
        if (tipo != TransacaoDTO.TipoTransacao.DESPESA || categoriaNome == null || categoriaNome.isBlank()) {
            return null;
        }
        Categoria existente = categoriaRepository.findByUsuarioIdAndNome(usuarioId, categoriaNome.trim());
        if (existente != null) {
            return existente.getId();
        }
        String norm = categoriaNome.trim().toLowerCase(Locale.ROOT);
        for (Categoria c : categoriaRepository.findByUsuarioIdOrderByNome(usuarioId)) {
            if (c.getNome() != null && c.getNome().toLowerCase(Locale.ROOT).contains(norm)) {
                return c.getId();
            }
        }
        return null;
    }

    private String nomeCategoria(Long categoriaId, String fallback) {
        if (categoriaId == null) {
            return fallback;
        }
        return categoriaRepository.findById(categoriaId)
            .map(Categoria::getNome)
            .orElse(fallback);
    }

    private ContaBancaria resolverConta(Long usuarioId, String banco) {
        List<ContaBancaria> matches = contaBancariaService.encontrarAtivasPorApelidoNormalizado(usuarioId, banco);
        if (matches.isEmpty()) {
            return contaBancariaService.resolverContaParaTransacao(usuarioId, null);
        }
        return matches.get(0);
    }

    private static LocalDateTime parseData(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDate.parse(raw.trim(), DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}
