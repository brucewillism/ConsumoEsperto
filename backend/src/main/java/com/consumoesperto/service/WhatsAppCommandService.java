package com.consumoesperto.service;

import com.consumoesperto.dto.CartaoCreditoDTO;
import com.consumoesperto.dto.EvolutionIncomingMessageDTO;
import com.consumoesperto.dto.MetaFinanceiraDTO;
import com.consumoesperto.dto.MetaFinanceiraRequest;
import com.consumoesperto.dto.RendaConfigDTO;
import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.Fatura;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.model.UsuarioAiConfig;
import com.consumoesperto.repository.CategoriaRepository;
import com.consumoesperto.repository.UsuarioAiConfigRepository;
import com.consumoesperto.service.entityupdate.WhatsAppEntityConfigUpdateService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.validation.ConstraintViolationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppCommandService {

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    private final OpenAiService openAiService;
    private final EvolutionApiService evolutionApiService;
    private final EvolutionMediaService evolutionMediaService;
    @SuppressWarnings("deprecation")
    private final TwilioWhatsAppService twilioWhatsAppService;
    private final TransacaoService transacaoService;
    private final CartaoCreditoService cartaoCreditoService;
    private final FaturaService faturaService;
    private final WhatsAppUserMappingService whatsAppUserMappingService;
    private final CategoriaRepository categoriaRepository;
    private final CnpjEnrichmentService cnpjEnrichmentService;
    private final MetaFinanceiraService metaFinanceiraService;
    private final WhatsAppBotAllowlist whatsAppBotAllowlist;
    private final UsuarioAiConfigRepository usuarioAiConfigRepository;
    private final WhatsAppEntityConfigUpdateService whatsAppEntityConfigUpdateService;
    private final InsightService insightService;
    private final ReportService reportService;
    private final RendaConfigService rendaConfigService;
    private final WhatsAppGestaoProativaService whatsAppGestaoProativaService;
    private final ParcelamentoService parcelamentoService;

    /** Confirmação de parcelamento com juros embutido (N×parcela > total citado). */
    private static final class ParcelaJurosEmbutidosDraft {
        BigDecimal valorAVista;
        BigDecimal valorParcela;
        int n;
        String descricao;
        String cardToken;
        TransacaoDTO.StatusConferencia status;
    }

    private final Map<Long, ParcelaJurosEmbutidosDraft> awaitingParcelaJurosEmbutidos = new ConcurrentHashMap<>();

    /** Aguardando usuário informar renda (texto) para concluir simulação de meta. */
    private final Map<Long, MetaDraft> awaitingIncome = new ConcurrentHashMap<>();
    /** Simulação concluída; aguardando confirmação para persistir meta. */
    private final Map<Long, MetaDraft> awaitingSaveConfirm = new ConcurrentHashMap<>();
    /** OCR de cupom aguardando confirmação (sim/não) antes de gravar transação. */
    private final Map<Long, PendingCupomDraft> awaitingCupomConfirm = new ConcurrentHashMap<>();
    /** Após SET_SALARY_CONFIG: pergunta se activa lançamento automático no dia de pagamento. */
    private final java.util.Set<Long> awaitingSalaryAutoConfirm = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

    public void processIncomingMessage(String from, String body, String mediaUrl, String mediaContentType) {
        Long userId = null;
        try {
            Optional<Usuario> ou = whatsAppUserMappingService.findByIncomingNumber(from);
            if (ou.isEmpty()) {
                log.debug("Webhook Twilio: numero nao vinculado, ignorado");
                return;
            }
            userId = ou.get().getId();
            if (whatsAppBotAllowlist.isConfigured(userId) && !whatsAppBotAllowlist.matchesMyNumber(from, userId)) {
                log.debug("Webhook Twilio: remetente nao autorizado para usuario {}", userId);
                return;
            }
            if (isImage(mediaUrl, mediaContentType)) {
                String response = handleImageReceipt(from, userId, mediaUrl, mediaContentType);
                sendOutgoingMessage(from, response, userId);
                return;
            }
            String sourceText = extractText(body, mediaUrl, mediaContentType, userId);
            Optional<String> metaReply = tryResolveMetaConversation(userId, sourceText);
            if (metaReply.isPresent()) {
                sendOutgoingMessage(from, metaReply.get(), userId);
                return;
            }
            Optional<String> parcelaJurosReply = tryResolveParcelaJurosEmbutidos(userId, sourceText);
            if (parcelaJurosReply.isPresent()) {
                sendOutgoingMessage(from, parcelaJurosReply.get(), userId);
                return;
            }
            JsonNode parsed = openAiService.parseCommand(sourceText, userId);
            String response;
            String act = parsed.path("action").asText("");
            if ("GENERATE_REPORT".equals(act) || "GERAR_RELATORIO".equals(act)) {
                response = handleGenerateReport(parsed, userId, from, sourceText, null);
            } else {
                response = executeCommand(parsed, userId, sourceText);
            }
            sendOutgoingIfPresent(from, response, userId);
        } catch (Exception e) {
            log.error("Erro ao processar comando WhatsApp", e);
            if (userId != null) {
                sendOutgoingMessage(from, "Nao consegui concluir o comando. Detalhes no log do servidor.", userId);
            }
        }
    }

    public void processIncomingEvolutionMessage(EvolutionIncomingMessageDTO incoming, Long userId, String evolutionInstanceName) {
        if (incoming == null) {
            log.warn("Payload Evolution vazio recebido.");
            return;
        }
        String from = incoming.getFromJid();
        boolean fromMe = incoming.isFromMe();
        final String evolutionApiKeyOverride = usuarioAiConfigRepository.findByUsuarioId(userId)
            .map(UsuarioAiConfig::getEvolutionApiKey)
            .filter(k -> k != null && !k.isBlank())
            .orElse(null);
        try {
            if (fromMe) {
                log.debug("Recebida mensagem de self-chat: from={}, mediaType={}, textPreview={}",
                    from,
                    incoming.getMediaType(),
                    preview(incoming.getText()));
            }
            byte[] mediaBytes = incoming.getMediaBytes();
            String mediaType = incoming.getMediaType();
            boolean audioOrImage = "audio".equalsIgnoreCase(mediaType) || "image".equalsIgnoreCase(mediaType);
            if (audioOrImage && (mediaBytes == null || mediaBytes.length == 0)
                && evolutionInstanceName != null && !evolutionInstanceName.isBlank()
                && incoming.getMessageKeyId() != null && !incoming.getMessageKeyId().isBlank()) {
                byte[] fromEvolution = evolutionMediaService.fetchBase64FromMediaMessage(
                    evolutionInstanceName.trim(), from, incoming.getMessageKeyId(), fromMe, evolutionApiKeyOverride);
                if (fromEvolution != null && fromEvolution.length > 0) {
                    mediaBytes = fromEvolution;
                }
            }
            if ((mediaBytes == null || mediaBytes.length == 0) && incoming.getMediaUrl() != null && !incoming.getMediaUrl().isBlank()) {
                mediaBytes = evolutionMediaService.fetchMedia(incoming.getMediaUrl(), evolutionApiKeyOverride);
            }

            String response;
            if (fromMe && "text".equalsIgnoreCase(resolveEffectiveMediaType(mediaType, incoming.getText()))
                && isSystemResponse(incoming.getText())) {
                log.debug("Ignorando auto-resposta do sistema");
                log.debug("Conteúdo identificado como resposta do robô. Abortando loop.");
                return;
            }
            if (fromMe) {
                log.debug("Conteúdo identificado como input do usuário. Seguindo...");
            }
            if ("image".equalsIgnoreCase(mediaType) && mediaBytes != null && mediaBytes.length > 0) {
                response = handleImageReceiptBytes(from, userId, mediaBytes, incoming.getMediaMimeType());
            } else {
                String sourceText = extractTextEvolution(incoming.getText(), mediaType, mediaBytes, incoming.getMediaMimeType(), userId);
                if ("audio".equalsIgnoreCase(mediaType) && (sourceText == null || sourceText.isBlank())) {
                    log.warn("Audio Evolution sem bytes ou transcricao vazia: userId={} from={} bytes={} evolutionInstance={}",
                        userId, from, mediaBytes == null ? -1 : mediaBytes.length, evolutionInstanceName);
                    response = "Não consegui obter ou transcrever o áudio.\n\n"
                        + "• Na Evolution: ativa *Webhook → Base64* para mídia no payload (ou confirma getBase64FromMediaMessage).\n"
                        + "• No app: vincula o *mesmo* número que envia as mensagens.\n"
                        + "• No servidor: chave Groq da plataforma (`GROQ_API_KEY` / `consumoesperto.ai.platform-groq-api-key`), "
                        + "e `evolution.url` + `evolution.apikey` alinhados à instância.";
                } else {
                    Optional<String> metaReply = tryResolveMetaConversation(userId, sourceText);
                    if (metaReply.isPresent()) {
                        response = metaReply.get();
                    } else {
                        Optional<String> pj = tryResolveParcelaJurosEmbutidos(userId, sourceText);
                        if (pj.isPresent()) {
                            response = pj.get();
                        } else {
                        JsonNode parsed = openAiService.parseCommand(sourceText, userId);
                        String actEv = parsed.path("action").asText("");
                        if ("GENERATE_REPORT".equals(actEv) || "GERAR_RELATORIO".equals(actEv)) {
                            response = handleGenerateReport(parsed, userId, from, sourceText, evolutionInstanceName);
                        } else {
                            response = executeCommand(parsed, userId, sourceText);
                        }
                        }
                    }
                }
            }
            sendOutgoingIfPresent(from, response, userId);
        } catch (Exception e) {
            log.error("Erro ao processar webhook Evolution", e);
            sendOutgoingMessage(from, userFacingEvolutionError(e), userId);
        }
    }

    private static String userFacingEvolutionError(Exception e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException cv) {
                return cv.getConstraintViolations().stream()
                    .findFirst()
                    .map(v -> "Não consegui salvar: " + v.getMessage() + ". Ajuste os dados ou reenvie o comando completo.")
                    .orElse("Não consegui salvar os dados. Tente de novo.");
            }
        }
        String m = e.getMessage() != null ? e.getMessage() : "";
        String lower = m.toLowerCase(Locale.ROOT);
        if (lower.contains("could not process file") || lower.contains("valid media file")) {
            return "Não consegui ler o áudio (ficheiro inválido ou corrompido ao chegar ao servidor). "
                + "Tenta gravar de novo ou envia em texto. Se usas Evolution, confirma que evolution.url e evolution.apikey "
                + "no backend coincidem com a tua instância.";
        }
        if (m.contains("Nenhum provedor de transcrição") || m.contains("nenhum provedor elegível")) {
            return "Para áudio, configure uma chave Groq ou OpenAI no app (Configurações IA / WhatsApp). "
                + "Ollama só é usado se definires a URL dele lá.";
        }
        if (m.contains("OLLAMA") && (m.contains("Connection refused") || m.contains("Failed to connect"))) {
            return "Transcrição tentou usar Ollama em localhost, mas não há servidor. "
                + "Inicia o Ollama ou configura Groq/OpenAI no app.";
        }
        if (m.contains("API_KEY não configurada") || m.contains("_API_KEY")) {
            return "Falta chave de API (Groq ou OpenAI). Configura no app em IA / WhatsApp.";
        }
        if (m.contains("Cartão de crédito já existe")) {
            return "Esse final já está num cartão *ativo*. Para mudar limite ou apelido: *edita o limite do Nubank para 7800*.";
        }
        if (m.contains("Valor nao informado") || m.contains("Valor não informado")) {
            return "Não encontrei o *valor* na mensagem. Ex.: *despesa 45,90 padaria* ou *receita 3500 salário*.";
        }
        if (m.contains("Valor da nota")) {
            return "Na foto da nota não consegui ler o *valor total*. Tenta outra foto mais nítida ou diz o valor por escrito.";
        }
        if (m.contains("file must be one of the following types")) {
            return "O áudio veio com tipo/extensão inválidos para a API. Atualize o app e tente de novo, ou envie texto.";
        }
        return "Nao consegui concluir o comando. Detalhes no log do servidor.";
    }

    private static String humanizarErroComando(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Ocorreu um erro ao processar o pedido.";
        }
        String m = raw;
        if (m.contains("Valor nao informado") || m.contains("Valor não informado")) {
            return "Falta o *valor* (ex.: *despesa 45,90 padaria*).";
        }
        if (m.contains("Usuário não encontrado")) {
            return "Conta de utilizador inválida. Volta a autenticar-te no app.";
        }
        return m;
    }

    /**
     * WhatsApp manda muitas vezes {@code audio/ogg; codecs=opus}. Se usarmos isso como extensão do ficheiro,
     * o Groq/OpenAI devolvem 400. Usar só o subtipo MIME e extensões que eles aceitam.
     */
    private static String normalizeAudioMimeForTranscription(String raw) {
        if (raw == null || raw.isBlank()) {
            return "audio/ogg";
        }
        String base = raw.split(";")[0].trim().toLowerCase(Locale.ROOT);
        if ("audio/ptt".equals(base)) {
            return "audio/ogg";
        }
        if (!base.startsWith("audio/")) {
            return "audio/ogg";
        }
        return base;
    }

    private static String filenameForTranscription(String normalizedMime) {
        String sub = normalizedMime.contains("/")
            ? normalizedMime.substring(normalizedMime.indexOf('/') + 1).trim()
            : "ogg";
        String ext = switch (sub) {
            case "mpeg", "mpga" -> "mp3";
            case "x-m4a" -> "m4a";
            default -> sub.isBlank() ? "ogg" : sub;
        };
        return "audio-command." + ext;
    }

    private String extractText(String body, String mediaUrl, String mediaContentType, Long userId) {
        if (mediaUrl != null && !mediaUrl.isBlank() && mediaContentType != null && mediaContentType.startsWith("audio/")) {
            byte[] media = twilioWhatsAppService.downloadMedia(mediaUrl);
            String mime = normalizeAudioMimeForTranscription(mediaContentType);
            String filename = filenameForTranscription(mime);
            String transcript = openAiService.transcribeAudio(media, filename, mime, userId);
            log.info("Comando de voz transcrito: {}", transcript);
            return transcript;
        }
        return body != null ? body : "";
    }

    private String extractTextEvolution(String body, String mediaType, byte[] mediaBytes, String mediaContentType, Long userId) {
        if ("audio".equalsIgnoreCase(mediaType) && mediaBytes != null && mediaBytes.length > 0) {
            String mime = normalizeAudioMimeForTranscription(
                mediaContentType != null ? mediaContentType : "audio/ogg"
            );
            String filename = filenameForTranscription(mime);
            String transcript = openAiService.transcribeAudio(mediaBytes, filename, mime, userId);
            log.info("Comando de voz Evolution transcrito: {}", transcript);
            return transcript;
        }
        return body != null ? body : "";
    }

    private String handleImageReceipt(String from, Long userId, String mediaUrl, String mediaContentType) {
        try {
            byte[] imageBytes = twilioWhatsAppService.downloadMedia(mediaUrl);
            return handleImageReceiptBytes(from, userId, imageBytes, mediaContentType);
        } catch (Exception ex) {
            log.warn("Falha ao processar imagem de nota no WhatsApp para {}: {}", from, ex.getMessage());
            return "Desculpe, não consegui ler esta nota. Pode enviar outra foto mais nítida?";
        }
    }

    private String handleImageReceiptBytes(String from, Long userId, byte[] imageBytes, String mediaContentType) {
        log.info("[VISION-LOG] Imagem recebida userId={} from={}", userId, from);
        JsonNode ocr = openAiService.analisarImagemNotaFiscal(imageBytes, mediaContentType, userId);
        double confianca = ocr.path("confianca").asDouble(0.0d);
        String erro = ocr.path("erro").asText("");
        if (!erro.isBlank() || confianca < 0.45d) {
            log.info("[VISION-LOG] OCR rejeitado userId={} confianca={} erro={}", userId, confianca, erro);
            return "Desculpe, não consegui ler esta nota. Pode enviar outra foto mais nítida?";
        }

        BigDecimal valor = parseValorOCR(ocr.path("valorTotal").asText(""));
        String estabelecimentoBruto = ocr.path("estabelecimento").asText("Compra via OCR");
        String estabelecimento = sanitizeDescription(estabelecimentoBruto);
        LocalDate dataCompra = parseDataOCR(ocr.path("dataCompra").asText(""));
        String categoriaSugerida = ocr.path("categoriaSugerida").asText("");
        String cnpjDigits = cnpjEnrichmentService.normalizeCnpj(ocr.path("cnpj").asText(""));

        String descricaoFinal = estabelecimento;
        if (cnpjDigits.length() == 14 && isNomeEstabelecimentoGenericoOuAusente(estabelecimentoBruto)) {
            descricaoFinal = cnpjEnrichmentService.enrich(cnpjDigits)
                .map(enr -> montarDescricaoEnriquecida(enr.nomeExibicao(), enr.enderecoFormatado()))
                .orElse(estabelecimento);
        }

        Long categoriaId = resolveCategoriaId(userId, categoriaSugerida);
        String categoriaNome = nomeCategoriaParaExibicao(userId, categoriaId, categoriaSugerida);

        PendingCupomDraft draft = new PendingCupomDraft();
        draft.valor = valor;
        draft.descricaoFinal = descricaoFinal;
        draft.dataTransacao = dataCompra.atStartOfDay();
        draft.categoriaId = categoriaId;
        draft.cnpj = cnpjDigits.length() == 14 ? cnpjDigits : null;
        awaitingCupomConfirm.put(userId, draft);
        log.info("[VISION-LOG] Rascunho cupom aguardando confirmação userId={} valor={} desc={}", userId, valor, descricaoFinal);

        return "📸 Vi que você gastou *" + BRL.format(valor) + "* no *" + descricaoFinal + "*. "
            + "Posso lançar isso na categoria *" + categoriaNome + "*?\n\n"
            + "Responda *sim* para confirmar ou *não* para cancelar.";
    }

    private String executeCommand(JsonNode cmd, Long userId, String sourceText) {
        String action = cmd.path("action").asText("UNKNOWN");
        double confianca = readConfianca(cmd);
        if (!"GET_INSIGHTS".equals(action) && !"CHECK_CARD_STATUS".equals(action) && !"GENERATE_REPORT".equals(action)
            && !"GERAR_RELATORIO".equals(action)
            && !"SET_SALARY_CONFIG".equals(action)
            && !"MANAGE_ENTITY".equals(action)
            && confianca < 0.55d) {
            return msgErro("Confiança da IA",
                "Não executei nada. Confiança " + String.format(Locale.US, "%.0f%%", confianca * 100)
                    + " — abaixo do mínimo seguro. Reenvia com valor, descrição e (se for despesa) cartão/banco explícitos.");
        }
        return switch (action) {
            case "CREATE_EXPENSE" -> handleExpense(cmd, userId, sourceText);
            case "CREATE_INCOME" -> handleIncome(cmd, userId, sourceText);
            case "CREATE_CARD" -> handleCard(cmd, userId, sourceText);
            case "UPDATE_ENTITY_CONFIG" -> formatarRespostaEntidade(whatsAppEntityConfigUpdateService.executar(userId, cmd));
            case "UPDATE_ACCOUNT_CONFIG" -> handleUpdateAccountConfig(cmd, userId, sourceText);
            case "SIMULATE_PURCHASE_GOAL" -> handleSimulatePurchaseGoal(cmd, userId, sourceText);
            case "GET_INSIGHTS" -> {
                String body = insightService.montarRecorrenciasWhatsapp(userId);
                if (body != null && body.startsWith("Não há despesas")) {
                    yield msgInfo("Recorrências", body);
                }
                yield msgOk("Recorrências", body);
            }
            case "CHECK_CARD_STATUS" -> handleCheckCardStatus(cmd, userId, sourceText);
            case "SET_SALARY_CONFIG" -> handleSetSalaryConfig(cmd, userId, sourceText);
            case "MANAGE_ENTITY" -> whatsAppGestaoProativaService.iniciarGestao(cmd, userId, sourceText);
            case "GENERATE_REPORT" -> msgInfo("Relatório PDF",
                "Para receber o PDF aqui no WhatsApp, usa a Evolution ligada a este número. No app: *Relatórios → PDF*.");
            default -> {
                String errorMessage = cmd.path("errorMessage").asText("");
                if (errorMessage.isBlank()) {
                    yield msgErro("Comando não reconhecido",
                        "Não percebi o pedido. Exemplos:\n• despesa 45,90 mercado\n• receita 3500 salário\n"
                            + "• cartão Nubank final 1234 vence 10\n• edita limite do Nubank para 5000");
                }
                yield msgErro("Dados em falta", errorMessage + "\nReenvia com mais detalhe.");
            }
        };
    }

    /** Respostas de {@link WhatsAppEntityConfigUpdateService} já trazem texto claro (incl. ✅ em sucesso). */
    private static String formatarRespostaEntidade(String resposta) {
        if (resposta == null || resposta.isBlank()) {
            return msgErro("Alteração no app", "Resposta vazia do servidor.");
        }
        String t = resposta.trim();
        if (t.startsWith("✅")) {
            return t;
        }
        return msgErro("Alteração no app", t);
    }

    private static String msgOk(String acao, String detalhe) {
        return "✅ *" + acao + "*\n" + detalhe;
    }

    private static String msgErro(String acao, String detalhe) {
        return "❌ *" + acao + "*\n" + detalhe;
    }

    private static String msgInfo(String acao, String detalhe) {
        return "ℹ️ *" + acao + "*\n" + detalhe;
    }

    private String handleCheckCardStatus(JsonNode cmd, Long userId, String sourceText) {
        String token = whatsAppFirstNonBlank(cmd.path("cardName").asText(""), cmd.path("bank").asText(""));
        if (token.isBlank()) {
            token = resolveCardToken(cmd, sourceText);
        }
        if (token.isBlank()) {
            return msgErro("Resumo de cartão / fatura",
                "Indica qual cartão (apelido ou banco). Ex.: *Quanto gastei no Nubank?* ou *resumo da fatura do Inter*.");
        }
        CardMatchResult match = findBestCard(userId, token);
        if (match.card == null) {
            log.info("[BILLING-LOG] Cartão não encontrado userId={} token={}", userId, token);
            return msgErro("Resumo de cartão / fatura",
                "Não há cartão *ativo* com esse nome. Se apagaste na app, o registo fica oculto — cadastra de novo (mesmo final) ou pede *cartão … final …* por aqui.");
        }
        return msgOk("Resumo do cartão", faturaService.montarResumoCartaoWhatsapp(userId, match.card));
    }

    /**
     * Gera PDF em memória e envia pela Evolution como documento. Não grava ficheiro em disco.
     */
    private String handleGenerateReport(JsonNode cmd, Long userId, String from, String sourceText, String evolutionInstanceHint) {
        YearMonth ym = resolveReportYearMonth(cmd, sourceText);
        String mesLabel = capitalizePt(ym.getMonth().getDisplayName(TextStyle.FULL, new Locale("pt", "BR")));

        String instance = whatsAppFirstNonBlank(
            evolutionInstanceHint != null ? evolutionInstanceHint : "",
            usuarioAiConfigRepository.findByUsuarioId(userId)
                .map(c -> c.getEvolutionInstanceName())
                .orElse("")
        );
        if (instance.isBlank()) {
            instance = null;
        }

        Optional<ReportService.RelatorioPdf> opt = reportService.gerarRelatorioMensal(
            userId, ym.getMonthValue(), ym.getYear());
        if (opt.isEmpty()) {
            return msgErro("Relatório PDF",
                "Não há dados suficientes para gerar o relatório de *" + mesLabel + "*. Lança algumas despesas/receitas e tenta de novo.");
        }
        try {
            evolutionApiService.enviarDocumentoPdf(from, opt.get().bytes(), opt.get().nomeArquivo(), instance);
        } catch (Exception e) {
            log.warn("[PDF-REPORT] Falha ao enviar documento Evolution: {}", e.getMessage());
            return msgErro("Envio do PDF",
                "O PDF de *" + mesLabel + "* foi gerado, mas falhou o envio pelo WhatsApp. Abre *Relatórios → PDF* no app. Detalhe: "
                    + e.getMessage());
        }
        return msgOk("Relatório PDF", "Enviei o ficheiro *" + opt.get().nomeArquivo() + "* acima nesta conversa.");
    }

    private static YearMonth resolveReportYearMonth(JsonNode cmd, String sourceText) {
        YearMonth now = YearMonth.now();
        int month = readOptionalPositiveInt(cmd, "reportMonth");
        int year = readOptionalPositiveInt(cmd, "reportYear");
        if (month < 1 || month > 12) {
            month = extrairMesDoTexto(sourceText).orElse(now.getMonthValue());
        }
        if (year < 2000 || year > 2100) {
            year = extrairAnoDoTexto(sourceText).orElse(now.getYear());
        }
        return YearMonth.of(year, month);
    }

    private static int readOptionalPositiveInt(JsonNode cmd, String field) {
        if (!cmd.has(field) || cmd.get(field).isNull()) {
            return 0;
        }
        JsonNode n = cmd.get(field);
        try {
            if (n.isInt() || n.isLong()) {
                return n.asInt();
            }
            String t = n.asText("").trim();
            if (t.isEmpty()) {
                return 0;
            }
            return Integer.parseInt(t.replaceAll("\\D", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private static Optional<Integer> extrairMesDoTexto(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String t = Normalizer.normalize(raw, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        Map<String, Integer> meses = mesesPt();
        for (Map.Entry<String, Integer> e : meses.entrySet()) {
            if (t.contains(e.getKey())) {
                return Optional.of(e.getValue());
            }
        }
        return Optional.empty();
    }

    private static Map<String, Integer> mesesPt() {
        Map<String, Integer> m = new HashMap<>();
        m.put("janeiro", 1);
        m.put("fevereiro", 2);
        m.put("marco", 3);
        m.put("março", 3);
        m.put("abril", 4);
        m.put("maio", 5);
        m.put("junho", 6);
        m.put("julho", 7);
        m.put("agosto", 8);
        m.put("setembro", 9);
        m.put("outubro", 10);
        m.put("novembro", 11);
        m.put("dezembro", 12);
        return m;
    }

    private static Optional<Integer> extrairAnoDoTexto(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        Matcher matcher = Pattern.compile("\\b(20[0-9]{2})\\b").matcher(raw);
        if (matcher.find()) {
            return Optional.of(Integer.parseInt(matcher.group(1)));
        }
        return Optional.empty();
    }

    private static String capitalizePt(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void sendOutgoingIfPresent(String to, String message, Long userId) {
        if (message != null && !message.isBlank()) {
            sendOutgoingMessage(to, message, userId);
        }
    }

    private Optional<String> tryResolveParcelaJurosEmbutidos(Long userId, String body) {
        ParcelaJurosEmbutidosDraft d = awaitingParcelaJurosEmbutidos.get(userId);
        if (d == null || body == null) {
            return Optional.empty();
        }
        String n = normalize(body);
        if (n.isBlank()) {
            return Optional.empty();
        }
        if (n.startsWith("nao") || n.equals("n") || n.contains("cancel")) {
            awaitingParcelaJurosEmbutidos.remove(userId);
            return Optional.of(msgInfo("Parcelamento", "Ok, não gravei nada."));
        }
        if (n.startsWith("sim") || n.equals("s") || n.contains("confirmo")) {
            CardMatchResult m = findBestCard(userId, d.cardToken);
            if (m.card == null) {
                awaitingParcelaJurosEmbutidos.remove(userId);
                return Optional.of(msgErro("Cartão", "Não encontrei o cartão. Envia de novo o comando com o nome do banco/cartão."));
            }
            try {
                List<TransacaoDTO> criadas = parcelamentoService.criarParcelamentoComJuros(
                    userId, m.card, d.descricao, d.n, d.valorParcela, d.valorAVista, d.status);
                awaitingParcelaJurosEmbutidos.remove(userId);
                return Optional.of(msgOk("Parcelamento com juros",
                    "Criei *" + criadas.size() + "* parcelas de *" + BRL.format(d.valorParcela) + "* no *"
                        + m.card.getNome() + "* (total financiado *"
                        + BRL.format(d.valorParcela.multiply(BigDecimal.valueOf(d.n))) + "*)."));
            } catch (Exception e) {
                awaitingParcelaJurosEmbutidos.remove(userId);
                return Optional.of(msgErro("Parcelamento", humanizarErroComando(e.getMessage())));
            }
        }
        return Optional.of(msgInfo("Confirmação", "Responde *sim* para gravar o parcelamento com juros ou *não* para cancelar."));
    }

    private String handleExpense(JsonNode cmd, Long userId, String sourceText) {
        try {
            BigDecimal amount = readAmount(cmd, sourceText);
            String description = cmd.path("description").asText("Despesa via WhatsApp");
            String cardToken = resolveCardToken(cmd, sourceText);
            CardMatchResult matchResult = findBestCard(userId, cardToken);
            TransacaoDTO.StatusConferencia status = matchResult.pendingReview
                ? TransacaoDTO.StatusConferencia.PENDENTE
                : TransacaoDTO.StatusConferencia.CONFIRMADA;

            int nParcelas = readOptionalPositiveInt(cmd, "installmentCount");
            if (nParcelas <= 0) {
                nParcelas = readOptionalPositiveInt(cmd, "installments");
            }
            BigDecimal installmentAmount = readOptionalBigDecimal(cmd, "installmentAmount");
            if (installmentAmount == null) {
                installmentAmount = readOptionalBigDecimal(cmd, "valorParcela");
            }
            boolean interestFree = cmd.path("interestFree").asBoolean(false)
                || textoIndicaSemJuros(sourceText);
            boolean withInterest = cmd.path("withInterest").asBoolean(false)
                || textoIndicaComJuros(sourceText);
            BigDecimal purchasePrice = readOptionalBigDecimal(cmd, "purchasePrice");

            if (nParcelas >= 2 && matchResult.card == null) {
                return msgErro("Parcelamento", "Para parcelar no cartão, indica o banco ou apelido (ex.: *no Nubank*).");
            }

            if (nParcelas >= 2 && matchResult.card != null && installmentAmount != null
                && installmentAmount.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal totalImplicit = installmentAmount.multiply(BigDecimal.valueOf(nParcelas)).setScale(2, RoundingMode.HALF_UP);
                if (!interestFree && !withInterest
                    && totalImplicit.subtract(amount).compareTo(new BigDecimal("0.02")) > 0) {
                    ParcelaJurosEmbutidosDraft d = new ParcelaJurosEmbutidosDraft();
                    d.valorAVista = amount.setScale(2, RoundingMode.HALF_UP);
                    d.valorParcela = installmentAmount.setScale(2, RoundingMode.HALF_UP);
                    d.n = nParcelas;
                    d.descricao = sanitizeDescription(description);
                    d.cardToken = cardToken;
                    d.status = status;
                    awaitingParcelaJurosEmbutidos.put(userId, d);
                    BigDecimal juros = totalImplicit.subtract(amount);
                    return msgInfo("Juros detectados",
                        "Detectei juros! O total será *" + BRL.format(totalImplicit) + "* (*" + BRL.format(juros)
                            + "* de juros sobre os *" + BRL.format(amount) + "* citados). Posso confirmar?\n\n"
                            + "Responde *sim* para gravar ou *não* para cancelar.");
                }
            }

            if (nParcelas >= 2 && matchResult.card != null) {
                if (interestFree) {
                    List<TransacaoDTO> criadas = parcelamentoService.criarParcelamentoSemJuros(
                        userId, matchResult.card, sanitizeDescription(description), amount, nParcelas, status);
                    return msgOk("Parcelamento sem juros",
                        "Criei *" + criadas.size() + "* parcelas de *"
                            + BRL.format(criadas.get(0).getValor()) + "* no *" + matchResult.card.getNome() + "*.");
                }
                if (withInterest && installmentAmount != null && installmentAmount.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal aVista = purchasePrice != null && purchasePrice.compareTo(BigDecimal.ZERO) > 0
                        ? purchasePrice
                        : amount;
                    List<TransacaoDTO> criadas = parcelamentoService.criarParcelamentoComJuros(
                        userId, matchResult.card, sanitizeDescription(description), nParcelas, installmentAmount, aVista, status);
                    return msgOk("Parcelamento com juros",
                        "Criei *" + criadas.size() + "* parcelas de *" + BRL.format(installmentAmount) + "* no *"
                            + matchResult.card.getNome() + "*.");
                }
            }

            TransacaoDTO dto = new TransacaoDTO();
            dto.setDescricao(sanitizeDescription(description));
            dto.setValor(amount);
            dto.setTipoTransacao(TransacaoDTO.TipoTransacao.DESPESA);
            dto.setDataTransacao(LocalDateTime.now());
            dto.setStatusConferencia(status);
            if (matchResult.card != null) {
                Fatura faturaAlvo = faturaService.resolverFaturaAbertaParaCartao(userId, matchResult.card);
                dto.setFaturaId(faturaAlvo.getId());
            }

            TransacaoDTO created = transacaoService.criarTransacao(dto, userId);
            String invoiceMessage = vincularNaFatura(matchResult, amount, userId);
            if (matchResult.card != null) {
                String detalhe = BRL.format(created.getValor()) + " em *" + created.getDescricao() + "* no cartão *"
                    + matchResult.card.getNome() + "*.\n" + invoiceMessage.trim();
                if (matchResult.pendingReview) {
                    detalhe += "\n⚠️ Ficou *pendente de conferência* (há mais do que um cartão parecido com o nome que disseste).";
                }
                return msgOk("Despesa registada", detalhe);
            }
            return msgOk("Despesa registada",
                BRL.format(created.getValor()) + " em *" + created.getDescricao() + "* (sem cartão associado).\n" + invoiceMessage.trim());
        } catch (RuntimeException e) {
            log.info("WhatsApp despesa: {}", e.getMessage());
            return msgErro("Despesa", humanizarErroComando(e.getMessage()));
        }
    }

    private boolean textoIndicaSemJuros(String sourceText) {
        if (sourceText == null) {
            return false;
        }
        String t = normalize(sourceText);
        return t.contains("sem juros") || t.contains("s juros") || t.contains("sjuros");
    }

    private boolean textoIndicaComJuros(String sourceText) {
        if (sourceText == null) {
            return false;
        }
        String t = normalize(sourceText);
        return t.contains("com juros") || t.contains("c juros");
    }

    private String handleIncome(JsonNode cmd, Long userId, String sourceText) {
        try {
            BigDecimal amount = readAmount(cmd, sourceText);
            String description = cmd.path("description").asText("Receita via WhatsApp");

            TransacaoDTO dto = new TransacaoDTO();
            dto.setDescricao(sanitizeDescription(description));
            dto.setValor(amount);
            dto.setTipoTransacao(TransacaoDTO.TipoTransacao.RECEITA);
            dto.setDataTransacao(LocalDateTime.now());

            TransacaoDTO created = transacaoService.criarTransacao(dto, userId);
            return msgOk("Receita registada",
                BRL.format(created.getValor()) + " em *" + created.getDescricao() + "*.");
        } catch (RuntimeException e) {
            log.info("WhatsApp receita: {}", e.getMessage());
            return msgErro("Receita", humanizarErroComando(e.getMessage()));
        }
    }

    private String handleSetSalaryConfig(JsonNode cmd, Long userId, String sourceText) {
        try {
            RendaConfigDTO dto = rendaConfigService.aplicarDeComandoJson(userId, cmd, sourceText);
            String body = montarResumoConfigSalarialWhatsapp(dto);
            BigDecimal pct = dto.getPercentualDescontosSobreBruto();
            if (pct != null && pct.compareTo(new BigDecimal("30")) > 0) {
                body += "\n\n⚠️ Atenção: os teus descontos fixos consomem *"
                    + pct.stripTrailingZeros().toPlainString() + "%* do salário bruto.";
            }
            if (dto.getSalarioLiquido().compareTo(BigDecimal.ZERO) > 0 && dto.getDiaPagamento() != null) {
                awaitingSalaryAutoConfirm.add(userId);
                body += "\n\nEntendido! O teu salário líquido é *" + BRL.format(dto.getSalarioLiquido()) + "*. "
                    + "Queres que eu lance essa *receita confirmada* automaticamente *todo dia "
                    + dto.getDiaPagamento() + "* no teu saldo? Responde *sim* ou *não*.";
            }
            return body;
        } catch (IllegalArgumentException e) {
            return msgErro("Configuração salarial", e.getMessage());
        } catch (Exception e) {
            log.warn("SET_SALARY_CONFIG: {}", e.getMessage());
            return msgErro("Configuração salarial",
                "Não consegui guardar. Envia bruto, descontos (valor + nome) e *dia de pagamento* (ex.: *dia 5*).");
        }
    }

    private static String montarResumoConfigSalarialWhatsapp(RendaConfigDTO dto) {
        BigDecimal td = dto.getTotalDescontos() != null ? dto.getTotalDescontos() : BigDecimal.ZERO;
        return "✅ *Configuração Salarial Salva!*\n\n"
            + "Bruto: *" + BRL.format(dto.getSalarioBruto()) + "*\n"
            + "Total Descontos: *" + BRL.format(td) + "*\n"
            + "Líquido (Receita Real): *" + BRL.format(dto.getSalarioLiquido()) + "*";
    }

    private String handleCard(JsonNode cmd, Long userId, String sourceText) {
        String cardName = cmd.path("cardName").asText("Cartao WhatsApp");
        String bank = cmd.path("bank").asText("Banco nao informado");
        int dueDay = cmd.path("dueDay").asInt(10);
        String rawNumber = cmd.path("cardNumber").asText("");

        BigDecimal creditFromCmd = readOptionalBigDecimal(cmd, "creditLimit");
        if (creditFromCmd == null) {
            creditFromCmd = readOptionalBigDecimal(cmd, "newLimit");
        }
        boolean limiteCreditoExplicito = creditFromCmd != null && creditFromCmd.compareTo(BigDecimal.ZERO) > 0;
        BigDecimal limiteCredito = limiteCreditoExplicito ? creditFromCmd : BigDecimal.valueOf(1000);

        BigDecimal availFromCmd = readOptionalBigDecimal(cmd, "newAvailableLimit");
        boolean limiteDisponivelExplicito = availFromCmd != null && availFromCmd.compareTo(BigDecimal.ZERO) >= 0;
        BigDecimal limiteDisponivel = limiteDisponivelExplicito ? availFromCmd : limiteCredito;

        CartaoCreditoDTO dto = new CartaoCreditoDTO();
        dto.setUsuarioId(userId);
        dto.setNome(sanitizeName(cardName));
        dto.setBanco(sanitizeName(bank));
        dto.setNumeroCartao(normalizeCardNumber(rawNumber));
        dto.setLimiteCredito(limiteCredito);
        dto.setLimiteDisponivel(limiteDisponivel);
        dto.setDiaVencimento(Math.max(1, Math.min(31, dueDay)));
        dto.setAtivo(true);

        boolean tinhaInativo = cartaoCreditoService.isCartaoInativoComNumero(userId, dto.getNumeroCartao());
        try {
            CartaoCreditoDTO created = cartaoCreditoService.criarCartaoCredito(dto);
            String finalDigits = created.getNumeroCartao().substring(created.getNumeroCartao().length() - 4);
            String corpo = "*" + created.getNome() + "* (" + created.getBanco() + "), final *" + finalDigits
                + "*, venc. dia *" + created.getDiaVencimento() + "*, limite *" + BRL.format(created.getLimiteCredito()) + "*. "
                + "Já aparece na lista de cartões.";
            if (tinhaInativo) {
                return msgOk("Cartão reativado",
                    "Havia um cartão *apagado na app* com o mesmo final; reativei e apliquei estes dados:\n" + corpo);
            }
            return msgOk("Cartão criado", corpo);
        } catch (RuntimeException e) {
            String m = e.getMessage() != null ? e.getMessage() : "";
            if (m.contains("já existe") || m.contains("ja existe")) {
                CartaoCreditoDTO updated = cartaoCreditoService.mergeCartaoExistentePorNumero(
                    dto, userId, limiteCreditoExplicito, limiteDisponivelExplicito);
                String finalDigits = updated.getNumeroCartao().substring(updated.getNumeroCartao().length() - 4);
                return msgOk("Cartão atualizado",
                    "Já existia um cartão *ativo* com final *" + finalDigits + "*. Atualizei: *" + updated.getNome()
                        + "* (" + updated.getBanco() + "), venc. dia *" + updated.getDiaVencimento()
                        + "*, limite *" + BRL.format(updated.getLimiteCredito()) + "*.");
            }
            log.warn("WhatsApp cartão: {}", m);
            return msgErro("Cartão", m != null && !m.isBlank() ? m : "Não foi possível concluir o cadastro do cartão.");
        }
    }

    /** Compatível com prompts antigos: monta {@code UPDATE_ENTITY_CONFIG} para cartão. */
    private String handleUpdateAccountConfig(JsonNode cmd, Long userId, String sourceText) {
        String cardRef = whatsAppFirstNonBlank(cmd.path("cardName").asText(""), cmd.path("bank").asText(""));
        if (cardRef.isBlank()) {
            return msgErro("Editar cartão", "Diz qual cartão (apelido) queres alterar. Ex.: *edita o limite do Nubank para 5000*.");
        }
        BigDecimal newLimit = readOptionalBigDecimal(cmd, "newLimit");
        if (newLimit == null) {
            newLimit = readOptionalBigDecimal(cmd, "newCreditLimit");
        }
        BigDecimal newAvail = readOptionalBigDecimal(cmd, "newAvailableLimit");
        String newName = whatsAppFirstNonBlank(cmd.path("newCardName").asText(""), cmd.path("newCardNickname").asText(""));
        if (newLimit == null && newAvail == null && newName.isBlank()) {
            return msgErro("Editar cartão",
                "Não percebi o que mudar. Indica novo *limite*, *limite disponível* ou *apelido*. Ex.: *aumenta o limite do Inter para 10000*.");
        }
        ObjectNode updates = JsonNodeFactory.instance.objectNode();
        if (newLimit != null) {
            updates.put("limite", newLimit.doubleValue());
        }
        if (newAvail != null) {
            updates.put("limiteDisponivel", newAvail.doubleValue());
        }
        if (!newName.isBlank()) {
            updates.put("apelido", newName);
        }
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("targetEntity", "CARTAO");
        root.put("identifier", cardRef);
        root.set("updates", updates);
        return formatarRespostaEntidade(whatsAppEntityConfigUpdateService.executar(userId, root));
    }

    private static String whatsAppFirstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        if (b != null && !b.isBlank()) {
            return b.trim();
        }
        return "";
    }

    private static BigDecimal readOptionalBigDecimal(JsonNode cmd, String field) {
        if (!cmd.has(field) || cmd.get(field).isNull()) {
            return null;
        }
        JsonNode n = cmd.get(field);
        if (n.isNumber()) {
            return BigDecimal.valueOf(n.asDouble());
        }
        String t = n.asText();
        if (t == null || t.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(t.replace(",", ".").trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private BigDecimal readAmount(JsonNode cmd, String sourceText) {
        if (!cmd.has("amount")) {
            BigDecimal extracted = extractAmountFromText(sourceText);
            if (extracted != null) {
                return extracted;
            }
            throw new RuntimeException("Valor nao informado");
        }
        return new BigDecimal(cmd.path("amount").asText("0"));
    }

    private BigDecimal extractAmountFromText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = Pattern.compile("(\\d+[\\.,]?\\d{0,2})").matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String number = matcher.group(1).replace(".", "").replace(",", ".");
        try {
            return new BigDecimal(number);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String resolveCardToken(JsonNode cmd, String sourceText) {
        String cardName = cmd.path("cardName").asText("");
        String bank = cmd.path("bank").asText("");
        if (!cardName.isBlank()) {
            return cardName;
        }
        if (!bank.isBlank()) {
            return bank;
        }
        String lower = normalize(sourceText);
        for (String token : new String[]{"nubank", "itau", "inter", "santander", "bradesco", "caixa", "bb", "picpay"}) {
            if (lower.contains(token)) {
                return token;
            }
        }
        return "";
    }

    private String vincularNaFatura(CardMatchResult matchResult, BigDecimal amount, Long userId) {
        if (matchResult.card == null) {
            return "• Fatura: não vinculada (cartão não informado)\n";
        }
        Fatura fatura = faturaService.registrarDespesaNoCartao(userId, matchResult.card, amount);
        String cardLabel = matchResult.card.getNome() + " (" + matchResult.card.getBanco() + ")";
        return "• Fatura atualizada: " + cardLabel + " | Nº " + fatura.getNumeroFatura() + " | Novo total R$ " + fatura.getValorFatura() + "\n";
    }

    private CardMatchResult findBestCard(Long userId, String cardToken) {
        if (cardToken == null || cardToken.isBlank()) {
            return new CardMatchResult(null, false);
        }
        List<CartaoCredito> candidatos = cartaoCreditoService.buscarAtivosPorNomeOuBancoAproximado(userId, cardToken);
        if (candidatos.isEmpty()) {
            return new CardMatchResult(null, false);
        }
        if (candidatos.size() == 1) {
            return new CardMatchResult(candidatos.get(0), false);
        }

        CartaoCredito principal = candidatos.get(0);
        BigDecimal limitePrincipal = principal.getLimiteDisponivel() != null ? principal.getLimiteDisponivel() : BigDecimal.ZERO;
        long empatados = candidatos.stream()
            .filter(c -> {
                BigDecimal limite = c.getLimiteDisponivel() != null ? c.getLimiteDisponivel() : BigDecimal.ZERO;
                return limite.compareTo(limitePrincipal) == 0;
            })
            .count();
        boolean pendente = empatados > 1;
        return new CardMatchResult(principal, pendente);
    }

    private String sanitizeDescription(String value) {
        if (value == null || value.isBlank()) {
            return "Transacao via WhatsApp";
        }
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String sanitized = ascii.replaceAll("[^a-zA-Z0-9\\s\\-.,!?()]", "").replaceAll("\\s+", " ").trim();
        return sanitized.length() < 3 ? "Transacao via WhatsApp" : sanitized;
    }

    private boolean isNomeEstabelecimentoGenericoOuAusente(String nomeOriginal) {
        if (nomeOriginal == null || nomeOriginal.isBlank()) {
            return true;
        }
        String n = normalize(nomeOriginal);
        if (n.length() < 3) {
            return true;
        }
        if (n.contains("compra via ocr") || n.equals("consumidor") || n.contains("nao identificado")) {
            return true;
        }
        return n.matches(".*\\b(pdv|operador|cupom|nota|mercado|loja|pagamento|estabelecimento)\\b.*");
    }

    private String montarDescricaoEnriquecida(String nomeOficial, String endereco) {
        String nome = sanitizeDescription(nomeOficial);
        if (endereco == null || endereco.isBlank()) {
            return truncarDescricao(nome);
        }
        String end = sanitizeDescription(endereco);
        String combo = nome + " - " + end;
        return truncarDescricao(combo);
    }

    private String truncarDescricao(String s) {
        if (s == null) {
            return "Transacao via WhatsApp";
        }
        if (s.length() <= 200) {
            return s;
        }
        return s.substring(0, 200);
    }

    private String sanitizeName(String value) {
        String sanitized = value == null ? "" : value.replaceAll("[^a-zA-Z0-9\\s\\-]", "").trim();
        return sanitized.isBlank() ? "Nao informado" : sanitized;
    }

    private String normalizeCardNumber(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("\\D", "");
        if (digits.length() >= 13 && digits.length() <= 19) {
            return digits;
        }
        if (digits.length() >= 4) {
            return "000000000" + digits.substring(digits.length() - 4);
        }
        return "0000000000000";
    }

    private String resolveEffectiveMediaType(String mediaType, String text) {
        if (mediaType != null && !mediaType.isBlank()) {
            return mediaType;
        }
        if (text != null && !text.isBlank()) {
            return "text";
        }
        return "";
    }

    public boolean isSystemResponse(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String trimmed = text.trim();
        for (String prefix : new String[]{"🚀", "📸", "📝", "✅", "❌", "⚠️"}) {
            if (trimmed.startsWith(prefix)) {
                return true;
            }
        }
        String normalized = normalize(trimmed);
        return normalized.contains("lancado com sucesso")
            || normalized.contains("desculpe, nao consegui ler")
            || normalized.contains("ola! notei que voce tem")
            || normalized.contains("erro ao processar comando")
            || normalized.contains("vi que voce gastou")
            || normalized.contains("posso lancar isso na categoria")
            || normalized.contains("resposta sim para lançar")
            || normalized.contains("meta salva no consumoesperto")
            || normalized.contains("nao identifiquei padroes claros")
            || normalized.contains("nao ha despesas confirmadas")
            || normalized.contains("estou a preparar o seu relatorio")
            || normalized.contains("segue o pdf acima")
            || normalized.contains("relatorio_export");
    }

    private String preview(String text) {
        if (text == null) {
            return "";
        }
        String t = text.replaceAll("\\s+", " ").trim();
        if (t.length() <= 60) {
            return t;
        }
        return t.substring(0, 60) + "...";
    }

    private void sendOutgoingMessage(String to, String message, Long userId) {
        String normalizedEvolution = evolutionApiService.normalizeToNumber(to);
        String instance = usuarioAiConfigRepository.findByUsuarioId(userId)
            .map(c -> c.getEvolutionInstanceName())
            .filter(s -> s != null && !s.isBlank())
            .orElse(null);
        try {
            evolutionApiService.enviarMensagem(normalizedEvolution, message, instance);
        } catch (Exception evolutionError) {
            log.warn("Falha ao enviar pela Evolution API (instancia={}, fallback Twilio): {}", instance, evolutionError.getMessage());
            try {
                twilioWhatsAppService.sendMessage(to, message);
            } catch (Exception twilioError) {
                log.error("Falha também no fallback Twilio: {}", twilioError.getMessage(), twilioError);
            }
        }
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase();
    }

    private double readConfianca(JsonNode cmd) {
        if (cmd.has("confianca")) {
            return cmd.path("confianca").asDouble(0.0d);
        }
        if (cmd.has("confidence")) {
            return cmd.path("confidence").asDouble(0.0d);
        }
        return 1.0d;
    }

    private boolean isImage(String mediaUrl, String mediaContentType) {
        return mediaUrl != null && !mediaUrl.isBlank()
            && mediaContentType != null
            && mediaContentType.startsWith("image/");
    }

    private BigDecimal parseValorOCR(String value) {
        if (value == null || value.isBlank()) {
            throw new RuntimeException("Valor da nota não encontrado");
        }
        String normalized = value.replace("R$", "").replace(" ", "").replace(".", "").replace(",", ".");
        return new BigDecimal(normalized);
    }

    private LocalDate parseDataOCR(String value) {
        if (value == null || value.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception ignored) {
            return LocalDate.now();
        }
    }

    private Long resolveCategoriaId(Long userId, String categoriaSugerida) {
        if (categoriaSugerida == null || categoriaSugerida.isBlank()) {
            return null;
        }
        String suggested = normalize(categoriaSugerida);
        return categoriaRepository.findByUsuarioIdOrderByNome(userId).stream()
            .filter(cat -> normalize(cat.getNome()).contains(suggested) || suggested.contains(normalize(cat.getNome())))
            .map(cat -> cat.getId())
            .findFirst()
            .orElse(null);
    }

    private String nomeCategoriaParaExibicao(Long userId, Long categoriaId, String categoriaSugeridaPeloOcr) {
        if (categoriaId != null) {
            return categoriaRepository.findById(categoriaId)
                .filter(c -> c.getUsuario() != null && userId.equals(c.getUsuario().getId()))
                .map(c -> c.getNome())
                .orElse(categoriaSugeridaPeloOcr != null && !categoriaSugeridaPeloOcr.isBlank()
                    ? categoriaSugeridaPeloOcr
                    : "Sem categoria");
        }
        if (categoriaSugeridaPeloOcr != null && !categoriaSugeridaPeloOcr.isBlank()) {
            return categoriaSugeridaPeloOcr;
        }
        return "Sem categoria";
    }

    private Optional<String> tryResolveMetaConversation(Long userId, String sourceText) {
        String text = sourceText != null ? sourceText.trim() : "";
        Optional<String> gestao = whatsAppGestaoProativaService.tentarConsumirResposta(userId, text);
        if (gestao.isPresent()) {
            return gestao;
        }
        if (awaitingSalaryAutoConfirm.contains(userId)) {
            if (isAffirmativeSaveReply(text)) {
                awaitingSalaryAutoConfirm.remove(userId);
                try {
                    rendaConfigService.definirReceitaAutomatica(userId, true);
                    return Optional.of(msgOk("Receita automática",
                        "Activado. No *dia de pagamento* (manhã, horário de Brasília) registo uma *receita confirmada* "
                            + "com o teu salário líquido, *uma vez por mês* — o saldo do dashboard soma receitas e despesas *confirmadas*."));
                } catch (Exception e) {
                    return Optional.of(msgErro("Receita automática", e.getMessage()));
                }
            }
            if (isNegativeReply(text)) {
                awaitingSalaryAutoConfirm.remove(userId);
                try {
                    rendaConfigService.definirReceitaAutomatica(userId, false);
                } catch (Exception ignored) {
                    // ignore
                }
                return Optional.of(msgInfo("Receita automática",
                    "Sem lançamento automático. O *Saldo atual* no app segue a soma de receitas menos despesas *confirmadas*."));
            }
            return Optional.of(msgInfo("Receita automática",
                "Responde *sim* para activar o lançamento no dia de pagamento ou *não* para manter só manual."));
        }
        if (awaitingCupomConfirm.containsKey(userId)) {
            if (isAffirmativeSaveReply(text)) {
                PendingCupomDraft d = awaitingCupomConfirm.remove(userId);
                try {
                    TransacaoDTO dto = new TransacaoDTO();
                    dto.setDescricao(d.descricaoFinal);
                    dto.setValor(d.valor);
                    dto.setTipoTransacao(TransacaoDTO.TipoTransacao.DESPESA);
                    dto.setDataTransacao(d.dataTransacao);
                    dto.setStatusConferencia(TransacaoDTO.StatusConferencia.CONFIRMADA);
                    dto.setCategoriaId(d.categoriaId);
                    dto.setCnpj(d.cnpj);
                    TransacaoDTO created = transacaoService.criarTransacao(dto, userId);
                    log.info("[VISION-LOG] Transação persistida após confirmação userId={} transacaoId={}", userId, created.getId());
                    return Optional.of(msgOk("Despesa do cupom",
                        BRL.format(created.getValor()) + " em *" + created.getDescricao() + "*. Vê no dashboard em *Transações*."));
                } catch (Exception e) {
                    log.warn("[VISION-LOG] Falha ao salvar transação do cupom: {}", e.getMessage());
                    return Optional.of(msgErro("Cupom / foto", "Não guardei o lançamento: " + e.getMessage()));
                }
            }
            if (isNegativeReply(text)) {
                awaitingCupomConfirm.remove(userId);
                log.info("[VISION-LOG] Utilizador cancelou cupom userId={}", userId);
                return Optional.of(msgInfo("Cupom / foto", "Não guardei o lançamento. Podes enviar outra foto quando quiseres."));
            }
            return Optional.of(msgInfo("Cupom / foto", "Responde *sim* para lançar a despesa do cupom ou *não* para cancelar."));
        }
        if (awaitingSaveConfirm.containsKey(userId)) {
            if (isAffirmativeSaveReply(text)) {
                MetaDraft d = awaitingSaveConfirm.remove(userId);
                try {
                    String alertaExtra = persistMetaFromDraft(userId, d);
                    String base = msgOk("Meta guardada", "Ficou registada no app em *Metas*.");
                    if (alertaExtra != null) {
                        base += "\n\n⚠️ " + alertaExtra;
                    }
                    return Optional.of(base);
                } catch (Exception e) {
                    log.warn("[META-LOG] Falha ao salvar meta: {}", e.getMessage());
                    return Optional.of(msgErro("Meta", "Não consegui guardar: " + e.getMessage()));
                }
            }
            if (isNegativeReply(text)) {
                awaitingSaveConfirm.remove(userId);
                return Optional.of(msgInfo("Meta", "Não guardei a meta. Podes pedir a simulação outra vez quando quiseres."));
            }
            return Optional.of(msgInfo("Meta", "Responde *sim* para guardar esta meta no app ou *não* para cancelar."));
        }
        if (awaitingIncome.containsKey(userId)) {
            BigDecimal informada = parseSingleMoneyValue(text);
            if (informada == null || informada.compareTo(BigDecimal.ZERO) <= 0) {
                return Optional.of(msgErro("Renda para a meta", "Não percebi o valor. Envia só o número (ex.: *5000* ou *3500,50*)."));
            }
            MetaDraft draft = awaitingIncome.remove(userId);
            draft.rendaUsada = informada;
            draft.rendaFromLancamentos = false;
            log.info("[META-LOG] Renda informada pelo usuário {} para simulação de meta.", userId);
            String msg = buildMetaSimulationMessage(userId, draft);
            awaitingSaveConfirm.put(userId, draft);
            return Optional.of(msg);
        }
        return Optional.empty();
    }

    private String persistMetaFromDraft(Long userId, MetaDraft d) {
        MetaFinanceiraRequest req = new MetaFinanceiraRequest();
        req.setDescricao(d.descricao);
        req.setValorTotal(d.valorTotal);
        req.setPercentualComprometimento(d.percentualComprometimento);
        req.setPrioridade(3);
        MetaFinanceiraDTO saved;
        if (d.rendaFromLancamentos) {
            saved = metaFinanceiraService.criar(req, userId);
        } else {
            saved = metaFinanceiraService.criarComRendaInformada(req, userId, d.rendaUsada);
        }
        if (saved.getAlertaComprometimento() != null) {
            return saved.getAlertaComprometimento();
        }
        return null;
    }

    private String handleSimulatePurchaseGoal(JsonNode cmd, Long userId, String sourceText) {
        awaitingIncome.remove(userId);
        awaitingSaveConfirm.remove(userId);
        awaitingCupomConfirm.remove(userId);
        awaitingSalaryAutoConfirm.remove(userId);
        whatsAppGestaoProativaService.cancelarSessao(userId);
        BigDecimal amount = readAmount(cmd, sourceText);
        BigDecimal pct = readPercentualMeta(cmd);
        String description = cmd.path("description").asText("Meta via WhatsApp");
        if (pct == null || pct.compareTo(BigDecimal.ZERO) <= 0 || pct.compareTo(new BigDecimal("100")) > 0) {
            return msgErro("Simulação de meta", "Indica o *percentual da renda* (entre 1 e 100). Ex.: *10* para 10%.");
        }
        MetaDraft draft = new MetaDraft();
        draft.descricao = sanitizeDescription(description);
        draft.valorTotal = amount;
        draft.percentualComprometimento = pct;
        log.info("[META-LOG] Calculando viabilidade para meta '{}' (usuario {})...", draft.descricao, userId);

        Optional<BigDecimal> rendaOpt = metaFinanceiraService.calcularRendaMensalMediaUltimosTresMeses(userId);
        if (rendaOpt.isEmpty()) {
            awaitingIncome.put(userId, draft);
            return msgInfo("Simulação de meta",
                "Para calcular, preciso da tua *renda mensal média*. Envia só o valor (ex.: *5000*).");
        }
        draft.rendaUsada = rendaOpt.get();
        draft.rendaFromLancamentos = true;
        String msg = buildMetaSimulationMessage(userId, draft);
        awaitingSaveConfirm.put(userId, draft);
        return msg;
    }

    private String buildMetaSimulationMessage(Long userId, MetaDraft d) {
        BigDecimal poupado = metaFinanceiraService.calcularValorPoupadoMensal(d.rendaUsada, d.percentualComprometimento);
        if (poupado.compareTo(BigDecimal.ZERO) <= 0) {
            return msgErro("Simulação de meta",
                "Com esse percentual a poupança mensal dá zero ou negativa. Aumenta o % da renda e tenta de novo.");
        }
        BigDecimal prazo = metaFinanceiraService.calcularPrazoMeses(d.valorTotal, poupado);
        String prazoTxt = formatPrazoLegivel(prazo);
        String base = String.format(
            "Com sua renda de %s, poupando %s (%s%% da renda), você terá sua %s em %s (cerca de %s meses). "
                + "Você está no caminho certo! 💪\n\nQuer que eu salve essa meta?",
            BRL.format(d.rendaUsada),
            BRL.format(poupado),
            d.percentualComprometimento.stripTrailingZeros().toPlainString(),
            d.descricao,
            prazoTxt,
            prazo.stripTrailingZeros().toPlainString()
        );
        BigDecimal proj = metaFinanceiraService.projetarComprometimentoComNovaMeta(userId, d.percentualComprometimento);
        String alerta = metaFinanceiraService.montarAlertaComprometimento(proj, true);
        if (alerta != null) {
            log.info("[META-LOG] Simulação: comprometimento projetado {}% (usuário {})", proj, userId);
            base += "\n\n⚠️ " + alerta;
        }
        return base;
    }

    private static String formatPrazoLegivel(BigDecimal prazoMeses) {
        double m = prazoMeses.doubleValue();
        if (m <= 0) {
            return "prazo indefinido";
        }
        int base = (int) Math.floor(m);
        double frac = m - base;
        if (frac >= 0.2 && frac <= 0.85) {
            if (base <= 0) {
                return "cerca de meio mês";
            }
            return base + " meses e meio";
        }
        int rounded = frac > 0.85 ? base + 1 : base;
        if (rounded < 1) {
            rounded = 1;
        }
        return rounded + (rounded == 1 ? " mês" : " meses");
    }

    private BigDecimal readPercentualMeta(JsonNode cmd) {
        JsonNode n = cmd.get("percentualComprometimento");
        if (n == null || n.isNull()) {
            return null;
        }
        try {
            if (n.isNumber()) {
                return BigDecimal.valueOf(n.asDouble()).setScale(2, RoundingMode.HALF_UP);
            }
            String t = n.asText("").replace(",", ".").trim();
            if (t.isEmpty()) {
                return null;
            }
            return new BigDecimal(t).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal parseSingleMoneyValue(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String t = text.replace("R$", "").replace("r$", "").trim();
        if (t.matches(".*\\d+[.,]\\d{3}([.,]\\d{2})?.*") || (t.contains(",") && t.lastIndexOf(',') > t.indexOf('.'))) {
            t = t.replace(".", "").replace(",", ".");
        } else {
            t = t.replace(",", ".");
        }
        try {
            return new BigDecimal(t.trim());
        } catch (Exception e) {
            return extractAmountFromText(text);
        }
    }

    private boolean isAffirmativeSaveReply(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String t = normalize(raw);
        return t.equals("sim")
            || t.equals("s")
            || t.startsWith("sim ")
            || t.startsWith("quero ")
            || t.contains("salva")
            || t.contains("pode salvar")
            || t.equals("confirmo")
            || t.startsWith("isso");
    }

    private boolean isNegativeReply(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String t = normalize(raw);
        return t.equals("nao")
            || t.equals("n")
            || t.startsWith("nao ")
            || t.contains("nao quero")
            || t.contains("cancela");
    }

    private static class MetaDraft {
        String descricao;
        BigDecimal valorTotal;
        BigDecimal percentualComprometimento;
        BigDecimal rendaUsada;
        boolean rendaFromLancamentos;
    }

    private static class PendingCupomDraft {
        BigDecimal valor;
        String descricaoFinal;
        LocalDateTime dataTransacao;
        Long categoriaId;
        String cnpj;
    }

    private static class CardMatchResult {
        private final CartaoCredito card;
        private final boolean pendingReview;

        private CardMatchResult(CartaoCredito card, boolean pendingReview) {
            this.card = card;
            this.pendingReview = pendingReview;
        }
    }
}
