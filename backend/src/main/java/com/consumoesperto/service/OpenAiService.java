package com.consumoesperto.service;

import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.service.ai.AiGatewayPromptContext;
import com.consumoesperto.service.ai.AiGatewayService;
import com.consumoesperto.util.AiProviderOrder;
import com.consumoesperto.service.AiProvidersConfigService.AiProvidersConfig;
import com.consumoesperto.service.AiProvidersConfigService.GroqSection;
import com.consumoesperto.service.AiProvidersConfigService.OllamaSection;
import com.consumoesperto.service.AiProvidersConfigService.OpenaiSection;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenAiService {

    private final ObjectMapper objectMapper;
    private final AiProvidersConfigService aiProvidersConfigService;
    private final UsuarioRepository usuarioRepository;
    private final JarvisProtocolService jarvisProtocolService;
    private final AiGatewayService aiGatewayService;

    /** Injeção lazy para quebrar o ciclo OpenAiService → Contexto → SaldoService → OpenAiService. */
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private JarvisContextoFinanceiroService jarvisContextoFinanceiroService;

    private RestTemplate restTemplate;

    @Value("${consumoesperto.ai.http.connect-timeout-ms:15000}")
    private int aiHttpConnectTimeoutMs;

    @Value("${consumoesperto.ai.http.read-timeout-ms:300000}")
    private int aiHttpReadTimeoutMs;

    @Value("${consumoesperto.ai.platform-gemini-api-key:}")
    private String platformGeminiApiKey;

    @Value("${consumoesperto.ai.gemini-base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String geminiBaseUrl;

    @Value("${consumoesperto.ai.gemini-model:gemini-2.5-flash}")
    private String geminiModel;

    @Value("${consumoesperto.ai.groq-model-document:llama-3.1-8b-instant}")
    private String groqModelDocument;

    @Value("${consumoesperto.ai.platform-claude-api-key:}")
    private String platformClaudeApiKey;

    @Value("${consumoesperto.ai.claude-base-url:https://api.anthropic.com}")
    private String claudeBaseUrl;

    @Value("${consumoesperto.ai.claude-model:claude-3-5-haiku-20241022}")
    private String claudeModel;

    @Value("${consumoesperto.ai.platform-deepseek-api-key:}")
    private String platformDeepseekApiKey;

    @Value("${consumoesperto.ai.deepseek-base-url:https://api.deepseek.com/v1}")
    private String deepseekBaseUrl;

    @Value("${consumoesperto.ai.deepseek-model:deepseek-chat}")
    private String deepseekModel;

    /** Modelo exclusivo OpenAI / endpoint compatível {@code /v1/embeddings}. */
    @Value("${consumoesperto.ai.embedding-model:text-embedding-3-small}")
    private String embeddingModel;

    @PostConstruct
    void initAiRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(5_000, aiHttpConnectTimeoutMs));
        factory.setReadTimeout(Math.max(30_000, aiHttpReadTimeoutMs));
        restTemplate = new RestTemplate(factory);
    }

    public String transcribeAudio(byte[] audioBytes, String filename, String contentType, Long userId) {
        AiProvidersConfig cfg = cfgForAi(userId);
        return executeAIRequestWithFallback(
            cfg,
            p -> canTranscribe(cfg, p),
            (p, c) -> transcribeForProvider(p, c, audioBytes, filename),
            "Nenhum provedor de transcrição disponível. Detalhes: "
        );
    }

    /** Monta o bloco de contexto financeiro da persona; nunca lança nem deixa placeholder literal. */
    private String montarContextoFinanceiroSeguro(Long userId) {
        if (userId == null || jarvisContextoFinanceiroService == null) {
            return "";
        }
        try {
            return jarvisContextoFinanceiroService.montarBlocoContexto(userId);
        } catch (Exception e) {
            log.debug("Contexto financeiro J.A.R.V.I.S. indisponível userId={}: {}", userId, e.getMessage());
            return "";
        }
    }

    public JsonNode parseCommand(String inputText, Long userId) {
        AiProvidersConfig cfg = cfgForAi(userId);
        Optional<Usuario> ou = userId == null ? Optional.empty() : usuarioRepository.findById(userId);
        Usuario uEnt = ou.orElse(null);
        String contextoFinanceiro = montarContextoFinanceiroSeguro(userId);
        String persona = jarvisProtocolService.camadaPersonaCompletaParaIa(uEnt, contextoFinanceiro);
        String systemPrompt = persona + "Você converte comandos financeiros em JSON estrito. " +
            "Retorne apenas JSON sem markdown. Campos: " +
            "action (CREATE_EXPENSE|CREATE_INCOME|CREATE_CARD|CREATE_BANK_ACCOUNT|CREATE_CATEGORY|CREATE_BUDGET|CREATE_META|UPDATE_ENTITY_CONFIG|UPDATE_ACCOUNT_CONFIG|SIMULATE_PURCHASE_GOAL|GET_INSIGHTS|CHECK_CARD_STATUS|LIST_CARDS|FORECAST_MONTH|GENERATE_REPORT|GERAR_RELATORIO|SET_SALARY_CONFIG|MANAGE_ENTITY|SPLIT_BILL|LIST_DEBTS|SETTLE_DEBT|UNKNOWN), " +
            "reportMonth (1-12, opcional), reportYear (ex.: 2026, opcional — default mês/ano correntes), " +
            "description, amount, bank, cardName, cardNumber, dueDay, creditLimit (limite total do cartão, opcional), " +
            "accountName (nome da conta bancária/carteira), accountType (CORRENTE|POUPANCA|DINHEIRO), initialBalance (saldo inicial da conta), " +
            "categoryName (nome da categoria), budgetLimit (limite do orçamento mensal), " +
            "installmentCount (N parcelas, inteiro ≥2), installmentAmount (valor de cada parcela quando citado), " +
            "paymentMethod (CONTA quando PIX/TED/débito/transferência na conta bancária; CARTAO quando compra ou gasto na fatura do cartão), " +
            "interestFree (true se 'sem juros'/'s/juros'), withInterest (true se 'com juros'), purchasePrice (preço à vista do bem quando citado), " +
            "newAvailableLimit (opcional), percentualComprometimento (0-100 quando for meta), " +
            "manageOperation (delete|edit), manageTarget (transacao|meta|cartao|conta_bancaria|categoria|orcamento), searchPhrase (termo de busca), " +
            "targetEntity (AUTO|CONTA|CARTAO|CONTA_BANCARIA|META|CATEGORIA|DESPESA_FIXA), identifier (apelido/nome do cadastro), " +
            "splitMembers (array de nomes/apelidos dos membros marcados no racha-contas, ex.: [\"Esposa\",\"Filho\"]), " +
            "counterpartyAlias (nome/apelido do membro ao quitar débito, ex.: \"Esposa\"), " +
            "updates (objeto JSON com campos a alterar, ex.: {\"limite\":5000,\"apelido\":\"Nubank Ultra\",\"icone\":\"shopping-cart\"}), " +
            "legado cartão: newLimit, newAvailableLimit, newCardName — use UPDATE_ACCOUNT_CONFIG ou UPDATE_ENTITY_CONFIG com updates.\n" +
            "confianca (0-1), errorMessage. " +
            "Se a frase citar cartão/banco (ex: 'paguei 20 no Nubank'), preencha cardName e/ou bank.";

        String userPrompt = "Texto do usuário: " + inputText + "\n" +
            "Regras:\n" +
            "- Se for despesa: action CREATE_EXPENSE e preencher description + amount.\n" +
            "- Parcelamento no cartão (CREATE_EXPENSE): obrigatório cartão (cardName/bank). " +
            "Sem juros (ex.: '100 reais no Nubank em 2 vezes sem juros'): installmentCount=2, interestFree=true, amount=valor total (100), purchasePrice vazio.\n" +
            "Com juros explícito (ex.: 'TV 2000 no Inter em 10 vezes de 250 com juros'): installmentCount=10, installmentAmount=250, withInterest=true, " +
            "purchasePrice=2000 (à vista), amount pode ser 250 (parcela) ou 2000 — o backend usa purchasePrice + installmentAmount.\n" +
            "Se o utilizador disser só 'N vezes de X' sem 'sem juros' nem 'com juros' e N*X > total citado, o backend pede confirmação de juros; " +
            "ainda assim preencha installmentCount, installmentAmount e amount com o total citado à vista quando existir.\n" +
            "- Em despesas: paymentMethod=CONTA para PIX, TED, transferência ou débito na conta (ex.: 'fiz um pix de 50 da conta Nubank'); " +
            "preencher accountName ou bank com o banco da conta; NÃO preencher cardName nesses casos. " +
            "paymentMethod=CARTAO para compras na fatura (ex.: 'gastei 50 no cartão Nubank', 'paguei no Nubank' sem PIX). " +
            "Quando houver referência só de cartão/fatura, preencher cardName/bank.\n" +
            "- Se for receita: action CREATE_INCOME e preencher description + amount.\n" +
            "- Se for cadastro de cartão: action CREATE_CARD e preencher cardName, bank, cardNumber (últimos 4 dígitos) e dueDay (1-31); " +
            "se a frase citar limite (ex.: 'limite 7800'), preencher creditLimit com o número; newAvailableLimit só se disser limite disponível separado.\n" +
            "- Se for cadastro de conta bancária/carteira (ex.: 'cria conta Nubank corrente saldo 1500', 'cadastra carteira dinheiro'): " +
            "action CREATE_BANK_ACCOUNT; accountName = nome; accountType = CORRENTE|POUPANCA|DINHEIRO; initialBalance ou amount = saldo inicial.\n" +
            "- Se for cadastro de categoria (ex.: 'cria categoria Pets', 'nova categoria Viagem'): action CREATE_CATEGORY; categoryName = nome.\n" +
            "- Se for cadastro de orçamento mensal (ex.: 'orçamento 800 em Alimentação', 'limite 500 para Lazer este mês'): " +
            "action CREATE_BUDGET; categoryName = categoria citada; budgetLimit ou amount = valor limite; reportMonth/reportYear se citar mês.\n" +
            "- Se for cadastrar/criar/registrar/adicionar *nova meta financeira* (objetivo de poupança), ex.: " +
            "'cadastra meta geladeira', 'criar uma meta chamada viagem', 'registrar meta notebook 5000 12%': " +
            "action CREATE_META; description = nome da meta; amount = valor total se citado; percentualComprometimento se citado. " +
            "NÃO use MANAGE_ENTITY para criar meta — MANAGE_ENTITY é só apagar/editar meta *existente*.\n" +
            "- Prioridade MANAGE_ENTITY: se a frase citar explicitamente *meta* (objetivo financeiro) para apagar/editar, preencha manageTarget=meta; " +
            "se citar *cartão/cartao/card*, manageTarget=cartao; se citar *conta bancária/carteira/poupança* (não cartão), manageTarget=conta_bancaria; " +
            "se citar *categoria*, manageTarget=categoria; se citar *orçamento/orcamento*, manageTarget=orcamento; " +
            "caso contrário manageTarget=transacao.\n" +
            "- Se houver mais de um item candidato a editar/apagar, o bot no WhatsApp deve listar numerado e pedir o número; " +
            "no JSON, preencha searchPhrase com o termo original (mesmo com erro de digitação; o backend corrige por similaridade).\n" +
            "- Se for editar cadastro existente (categoria, meta, despesa fixa, cartão/conta), use UPDATE_ENTITY_CONFIG: " +
            "targetEntity = AUTO se o usuário não especificar tipo (o sistema busca categoria, meta, despesa fixa, conta bancária, cartão); " +
            "identifier = nome citado; updates = mapa com chaves canônicas: " +
            "cartão: apelido, banco, limite, limiteDisponivel, cor, icone; " +
            "conta bancária (targetEntity CONTA_BANCARIA): nome, tipo, saldo, padrao; " +
            "categoria: nome, cor, icone (limiteMensal pode ser pedido mas o app pode ignorar); " +
            "meta: nome ou descricao, valorObjetivo ou valorTotal, percentual, prioridade, dataPrazo (yyyy-MM-dd); " +
            "despesa fixa: descricao, valor.\n" +
            "- Atalho legado só para cartão: UPDATE_ACCOUNT_CONFIG com cardName, newLimit, newAvailableLimit, newCardName.\n" +
            "- Se o usuário quiser *simular prazo* de compra/meta (ex: 'quero comprar uma TV de 2000 usando 10% da minha renda', " +
            "'quanto tempo para geladeira 3500 comprometendo 15% do salário'): action SIMULATE_PURCHASE_GOAL, description = item, " +
            "amount = valor total do bem, percentualComprometimento = percentual informado (número, ex: 10 para 10%).\n" +
            "- Se perguntar sobre recorrência, assinaturas repetidas, gastos fixos mensais (ex: 'tenho recorrência?', 'o que repete?'): action GET_INSIGHTS.\n" +
            "- Se pedir listar/quantos cartões tem (ex.: 'lista meus cartões', 'quantos cartões eu tenho?'): action LIST_CARDS.\n" +
            "- Se perguntar quanto gastou no cartão, resumo de fatura, limite disponível (ex: 'quanto gastei no Nubank?', 'resumo da fatura do Inter'): " +
            "action CHECK_CARD_STATUS e preencher cardName e/ou bank com o cartão citado.\n" +
            "- Se perguntar como vai fechar o mês, se vai ficar no vermelho, previsão/projeção do mês ou saldo no fim do mês: action FORECAST_MONTH.\n" +
            "- Se perguntar onde investir o saldo, saldo parado rendendo, poupança vs CDB vs Tesouro Selic: action SUGERIR_INVESTIMENTO.\n" +
            "- Se perguntar se vale a pena comprar agora no cartão, quando a fatura fecha/vira, melhor dia para comprar, " +
            "prazo de pagamento ou alavancagem de caixa (ex: 'vale a pena comprar agora no Nubank?', 'quando meu cartão vira?', " +
            "'é bom comprar notebook hoje no Inter?'): action CHECK_CARD_STATUS com cardName/bank; o sistema responderá com " +
            "estratégia de fechamento e vencimento (foco em prazo, não só saldo).\n" +
            "- Se pedir relatório ou PDF mensal (ex: 'gera um PDF do mês', 'relatório de maio', 'quero o resumo em PDF'): " +
            "action GENERATE_REPORT; preencher reportMonth e reportYear quando a frase citar mês/ano (ex: maio 2026 → 5 e 2026).\n" +
            "- Sinónimo: 'gerar relatorio', 'GERAR_RELATORIO', 'manda o pdf' → action GERAR_RELATORIO (mesmos campos que GENERATE_REPORT).\n" +
            "- Apagar ou editar algo pelo nome (ex: 'apague a gasolina deste mês', 'edite minha meta de Lazer', 'apague meu cartão Inter', " +
            "'inativa conta Nubank', 'apaga categoria Pets', 'remove orçamento de Alimentação'): " +
            "action MANAGE_ENTITY; manageOperation = delete ou edit; manageTarget = transacao | meta | cartao | conta_bancaria | categoria | orcamento; " +
            "searchPhrase = termo principal (ex.: gasolina, Lazer, Inter); para transações no mês corrente use reportMonth/reportYear ou deixe vazio para mês atual; " +
            "tipoTransacao DESPESA ou RECEITA quando for transação (default DESPESA se for gasto).\n" +
            "- Se configurar salário / renda com descontos (ex: 'salário bruto 8000, 600 INSS, 400 plano, 500 IRRF, pagamento dia 5'): " +
            "action SET_SALARY_CONFIG; preencher updates como objeto JSON: " +
            "salarioBruto (número), diaPagamento (1-31), descontosFixos como array de objetos " +
            "{ \"rotulo\": \"INSS\", \"valor\": 600 } (use rotulo ou label; valor ou amount numérico). " +
            "Se a frase não disser o dia, inferir com cuidado ou usar UNKNOWN pedindo o dia.\n" +
            "- Racha-contas / dividir despesa no grupo familiar (ex.: 'racha os 150 do restaurante entre eu, a Esposa e o Filho', " +
            "'divide a conta de 90 comigo e com o João', 'racha 60 com a Maria'): action SPLIT_BILL; amount = valor total; " +
            "description = motivo/local (ex.: restaurante); splitMembers = array com os nomes dos OUTROS membros marcados " +
            "(NÃO inclua o próprio usuário/'eu'/'mim'). Ex.: 'racha 150 entre eu, Esposa e Filho' → amount=150, splitMembers=[\"Esposa\",\"Filho\"].\n" +
            "- Consultar quem está devendo / pendências do grupo (ex.: 'quem está me devendo?', 'quais minhas pendências no grupo?', " +
            "'meu balanço da família'): action LIST_DEBTS.\n" +
            "- Quitar/acertar débito interno do grupo (ex.: 'acertei os 50 com a Esposa', 'a Maria me pagou', 'quitei com o João'): " +
            "action SETTLE_DEBT; counterpartyAlias = nome do membro com quem acertou.\n" +
            "- Se faltar dado essencial, retornar action UNKNOWN com errorMessage explicando o que faltou.\n" +
            "- amount deve ser número decimal sem símbolo de moeda.\n" +
            "- Sempre retornar o campo confianca com valor entre 0 e 1.";

        PromptPar otimizado = aplicarSuppressorAntesDaIa(userId, systemPrompt, userPrompt, cfg, false);
        return executeAIRequestWithFallback(
            cfg,
            p -> canChatJson(cfg, p),
            (p, c) -> {
                String model = chatModelFor(p, c);
                return parseChatJsonForProvider(p, c, model, otimizado.system(), otimizado.user());
            },
            "Nao foi possivel processar IA (Groq/OpenAI/Claude/Gemini/DeepSeek/Ollama). Detalhes: "
        );
    }

    /**
     * Resposta analítica usando contexto RAG (transações indexadas em pgvector).
     */
    public String gerarRespostaAnaliticaRag(Long userId, String pergunta, String contextoTransacoes) {
        if (userId == null || pergunta == null || pergunta.isBlank()
            || contextoTransacoes == null || contextoTransacoes.isBlank()) {
            return "";
        }
        Usuario u = usuarioRepository.findById(userId).orElse(null);
        String contextoFinanceiro = montarContextoFinanceiroSeguro(userId);
        String persona = jarvisProtocolService.camadaPersonaCompletaParaIa(u, contextoFinanceiro);
        String system = persona
            + "Com base *somente* nos trechos de transações fornecidos, responda à pergunta com análise útil "
            + "(totais, padrões, tendências). Se os trechos não forem suficientes, indique-o com franqueza. "
            + "Texto corrido; listas curtas permitidas. Retorne JSON {\"texto\":\"...\"}.";
        String userPrompt = "Pergunta: " + pergunta + "\n\nTrechos indexados (RAG / pgvector):\n" + contextoTransacoes;
        return gerarTexto(
            userId,
            system,
            userPrompt,
            "Ainda não há memória semântica de transações suficiente para responder com precisão. Faça mais lançamentos ou reformule."
        );
    }

    /**
     * Uma linha de insight para o PDF mensal (dados já agregados; não expõe outras contas).
     */
    public String gerarInsightRelatorioMaiorGasto(
        Long userId,
        int mes,
        int ano,
        String categoriaMaiorGasto,
        BigDecimal valorMaiorGasto,
        BigDecimal totalDespesasMes
    ) {
        AiProvidersConfig cfg = cfgForAi(userId);
        String systemPrompt = "Retorne estritamente JSON sem markdown: {\"insight\":\"...\"}. "
            + "O campo insight deve ser UMA única frase curta (máximo 160 caracteres), em português europeu, "
            + "tom profissional e neutro, comentando o maior gasto do mês em relação ao total. Sem emojis.";
        String userPrompt = "Período: " + String.format("%02d/%d", mes, ano)
            + ". Categoria com maior despesa: " + categoriaMaiorGasto
            + ". Valor nessa categoria: " + valorMaiorGasto.stripTrailingZeros().toPlainString()
            + ". Total de despesas confirmadas no mês: " + totalDespesasMes.stripTrailingZeros().toPlainString() + ".";

        try {
            PromptPar otimizado = aplicarSuppressorAntesDaIa(userId, systemPrompt, userPrompt, cfg, false);
            String linha = executeAIRequestWithFallback(
                cfg,
                p -> canChatJson(cfg, p),
                (p, c) -> {
                    String model = chatModelFor(p, c);
                    JsonNode j = parseChatJsonForProvider(p, c, model, otimizado.system(), otimizado.user());
                    return j.path("insight").asText("").trim();
                },
                "Insight relatório: "
            );
            if (linha.isBlank()) {
                return fallbackInsight(categoriaMaiorGasto, valorMaiorGasto, totalDespesasMes);
            }
            return linha.length() > 200 ? linha.substring(0, 197) + "…" : linha;
        } catch (Exception e) {
            log.warn("Insight relatório PDF indisponível: {}", e.getMessage());
            return fallbackInsight(categoriaMaiorGasto, valorMaiorGasto, totalDespesasMes);
        }
    }

    private static String fallbackInsight(
        String categoriaMaiorGasto,
        BigDecimal valorMaiorGasto,
        BigDecimal totalDespesasMes
    ) {
        if (totalDespesasMes == null || totalDespesasMes.compareTo(BigDecimal.ZERO) <= 0) {
            return "Concentre-se em categorizar e rever despesas no app para ganhar previsibilidade.";
        }
        BigDecimal pct = valorMaiorGasto != null
            ? valorMaiorGasto.multiply(BigDecimal.valueOf(100)).divide(totalDespesasMes, 0, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        return "O maior volume mensal concentrou-se em «" + categoriaMaiorGasto + "» (~" + pct + "% do total) — vale rever se está alinhado com as suas metas.";
    }

    public JsonNode analisarImagemNotaFiscal(String imageUrl, Long userId) {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new RuntimeException("URL da imagem não informada");
        }
        return analisarImagemNotaFiscalConteudo(imageUrl, userId);
    }

    public JsonNode analisarImagemNotaFiscal(byte[] imageBytes, String contentType, Long userId) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new RuntimeException("Imagem não informada para OCR");
        }
        String safeContentType = (contentType == null || contentType.isBlank()) ? "image/jpeg" : contentType;
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        String dataUrl = "data:" + safeContentType + ";base64," + base64;
        return analisarImagemNotaFiscalConteudo(dataUrl, userId);
    }

    private JsonNode analisarImagemNotaFiscalConteudo(String imageSource, Long userId) {
        log.info("[VISION-LOG] Iniciando OCR de cupom userId={}", userId);
        AiProvidersConfig cfg = cfgForAi(userId);
        String systemPrompt = "Você é um extrator OCR financeiro especializado em cupons/notas fiscais brasileiras. " +
            "Analise a imagem e retorne estritamente JSON sem markdown.";
        String userPrompt = "Analise esta imagem de cupom fiscal e extraia os campos: " +
            "valorTotal (double), estabelecimento (string), dataCompra (yyyy-MM-dd), categoriaSugerida (string), " +
            "cnpj (string, se visível), confianca (0 a 1), erro (string opcional). " +
            "Se não for possível ler com segurança, retorne erro preenchido e confianca baixa.";

        AiGatewayService.GatewayOptimizationResult visionOpt = aiGatewayService.optimizeBeforeProvider(
            userId,
            systemPrompt,
            userPrompt,
            AiGatewayPromptContext.fromPrompts(systemPrompt, userPrompt, false, true),
            resolveTargetModelForSuppressor(cfg, false)
        );
        final String visionSys = visionOpt.systemPrompt();
        final String visionUsr = visionOpt.userPrompt();

        JsonNode out = executeAIRequestWithFallback(
            cfg,
            p -> canVision(cfg, p),
            (p, c) -> {
                String model = visionModelFor(p, c);
                log.info("[VISION-LOG] Provedor={} modelo={} userId={}", p.name(), model, userId);
                return parseVisionOpenAiCompatible(
                    p.name(), apiKeyFor(p, c), baseUrlFor(p, c), model, visionSys, visionUsr, imageSource);
            },
            "Falha OCR em todos provedores (Groq/OpenAI/Ollama): "
        );
        log.info("[VISION-LOG] OCR concluído userId={} confianca={}", userId, out.path("confianca").asDouble(Double.NaN));
        return out;
    }

    public JsonNode gerarJson(Long userId, String systemPrompt, String userPrompt) {
        return gerarJsonInternal(userId, systemPrompt, userPrompt, false);
    }

    /** Extração de PDF/contracheque: modelo Groq mais leve para poupar quota diária. */
    public JsonNode gerarJsonDocumento(Long userId, String systemPrompt, String userPrompt) {
        return gerarJsonInternal(userId, systemPrompt, userPrompt, true);
    }

    private JsonNode gerarJsonInternal(Long userId, String systemPrompt, String userPrompt, boolean documento) {
        AiProvidersConfig cfg = cfgForAi(userId);
        PromptPar otimizado = aplicarSuppressorAntesDaIa(userId, systemPrompt, userPrompt, cfg, documento);
        final String systemFinal = otimizado.system();
        final String userFinal = otimizado.user();
        return executeAIRequestWithFallback(
            cfg,
            p -> canChatJson(cfg, p),
            (p, c) -> {
                String model = documento ? chatModelForDocument(p, c) : chatModelFor(p, c);
                return parseChatJsonForProvider(p, c, model, systemFinal, userFinal);
            },
            "Nao foi possivel gerar JSON via IA. Detalhes: "
        );
    }

    /** AI Gateway (ATS + estratégia AUTO) antes do fallback de providers. */
    private PromptPar aplicarSuppressorAntesDaIa(
        Long userId, String systemPrompt, String userPrompt, AiProvidersConfig cfg, boolean documento
    ) {
        String sys = systemPrompt != null ? systemPrompt : "";
        String usr = userPrompt != null ? userPrompt : "";
        AiGatewayPromptContext ctx = AiGatewayPromptContext.builder()
            .systemPrompt(sys)
            .userPrompt(usr)
            .documentImport(documento)
            .jsonOutput(true)
            .build();
        AiGatewayService.GatewayOptimizationResult opt = aiGatewayService.optimizeBeforeProvider(
            userId, sys, usr, ctx, resolveTargetModelForSuppressor(cfg, documento));
        if (opt.optimizedByAts()) {
            log.info(
                "[AiGateway] strategy={} saved~{} cache={}",
                opt.strategyUsed(), opt.tokensSaved(), opt.fromCache());
        }
        return new PromptPar(opt.systemPrompt(), opt.userPrompt());
    }

    private record PromptPar(String system, String user) {}

    private String resolveTargetModelForSuppressor(AiProvidersConfig cfg, boolean documento) {
        for (AiProviderType p : AiProviderOrder.canonicalTypes()) {
            if (!canChatJson(cfg, p)) {
                continue;
            }
            if (p == AiProviderType.GROQ && documento) {
                return chatModelForDocument(p, cfg);
            }
            return chatModelFor(p, cfg);
        }
        return documento && groqModelDocument != null && !groqModelDocument.isBlank()
            ? groqModelDocument
            : "llama-3.1-8b-instant";
    }

    private String chatModelForDocument(AiProviderType p, AiProvidersConfig cfg) {
        if (p == AiProviderType.GROQ) {
            if (groqModelDocument != null && !groqModelDocument.isBlank()) {
                return groqModelDocument.trim();
            }
            String m = groq(cfg).getModelText();
            if (m != null && (m.contains("70b") || m.contains("versatile"))) {
                return "llama-3.1-8b-instant";
            }
            return m != null && !m.isBlank() ? m : "llama-3.1-8b-instant";
        }
        return chatModelFor(p, cfg);
    }

    public String gerarTexto(Long userId, String systemPrompt, String userPrompt, String fallback) {
        try {
            JsonNode json = gerarJson(userId,
                systemPrompt + " Retorne estritamente JSON sem markdown no formato {\"texto\":\"...\"}.",
                userPrompt);
            String texto = json.path("texto").asText("").trim();
            return texto.isBlank() ? fallback : texto;
        } catch (Exception e) {
            log.warn("Geração de texto IA indisponível: {}", e.getMessage());
            return fallback;
        }
    }

    /**
     * Embeddings OpenAI-compatíveis ({@code POST .../embeddings}). A camada PG usa 1536 dimensões.
     */
    public Optional<float[]> tryCreateEmbedding(String text, Long userId) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(createEmbeddingOpenAi(text.trim(), userId));
        } catch (Exception e) {
            log.warn("Embedding indisponível: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private float[] createEmbeddingOpenAi(String text, Long userId) throws Exception {
        AiProvidersConfig cfg = cfgForAi(userId);
        OpenaiSection o = openai(cfg);
        if (!hasKey(o) || !hasUrl(o.getBaseUrl())) {
            throw new RuntimeException("OpenAI (embeddings): API key ou base URL ausente");
        }
        String model = embeddingModel == null || embeddingModel.isBlank() ? "text-embedding-3-small" : embeddingModel.trim();
        Map<String, Object> payload = Map.of("model", model, "input", text);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(o.getApiKey().trim());
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        String url = trimTrailingSlash(o.getBaseUrl()) + "/embeddings";
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        JsonNode root = objectMapper.readTree(response.getBody());
        JsonNode emb = root.path("data").path(0).path("embedding");
        if (!emb.isArray() || emb.size() == 0) {
            throw new RuntimeException("Resposta de embeddings sem vetor");
        }
        int n = emb.size();
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            out[i] = (float) emb.get(i).asDouble();
        }
        return out;
    }

    /**
     * Percorre {@link AiProvidersConfig#getProviderOrder()} (completando provedores faltantes) até o primeiro sucesso.
     */
    private <T> T executeAIRequestWithFallback(
        AiProvidersConfig cfg,
        Predicate<AiProviderType> canUseProvider,
        FallbackAttempt<T> attempt,
        String failureMessagePrefix
    ) {
        return executeAIRequestWithFallback(cfg, orderedProviders(cfg), canUseProvider, attempt, failureMessagePrefix);
    }

    private <T> T executeAIRequestWithFallback(
        AiProvidersConfig cfg,
        List<AiProviderType> providerOrder,
        Predicate<AiProviderType> canUseProvider,
        FallbackAttempt<T> attempt,
        String failureMessagePrefix
    ) {
        List<String> errors = new ArrayList<>();
        for (AiProviderType provider : providerOrder) {
            if (!canUseProvider.test(provider)) {
                continue;
            }
            log.debug("Usando provedor {} (config usuario)", provider.name());
            try {
                return attempt.execute(provider, cfg);
            } catch (Exception e) {
                errors.add(provider.name() + ": " + e.getMessage());
                log.warn("Falha no provedor {}: {}", provider.name(), e.getMessage());
            }
        }
        if (errors.isEmpty()) {
            String geminiHint = canChatJson(cfg, AiProviderType.GEMINI)
                ? ""
                : " GEMINI: chave ausente — defina GEMINI_API_KEY no .env do servidor.";
            throw new RuntimeException(
                failureMessagePrefix + "nenhum provedor elegível (credenciais/URL ausentes)." + geminiHint);
        }
        appendMissingPlatformProviders(cfg, errors);
        throw new RuntimeException(failureMessagePrefix + String.join(" | ", errors));
    }

    @FunctionalInterface
    private interface FallbackAttempt<T> {
        T execute(AiProviderType provider, AiProvidersConfig cfg) throws Exception;
    }

    private List<AiProviderType> orderedProviders(AiProvidersConfig cfg) {
        return AiProviderOrder.canonicalTypes();
    }

    private boolean canChatJson(AiProvidersConfig cfg, AiProviderType p) {
        return switch (p) {
            case GROQ -> hasKey(groq(cfg)) && hasUrl(groq(cfg).getBaseUrl());
            case OPENAI -> hasKey(openai(cfg)) && hasUrl(openai(cfg).getBaseUrl());
            case CLAUDE -> platformClaudeApiKey != null && !platformClaudeApiKey.isBlank() && hasUrl(claudeBaseUrl);
            case GEMINI -> platformGeminiApiKey != null && !platformGeminiApiKey.isBlank() && hasUrl(geminiBaseUrl);
            case DEEPSEEK -> platformDeepseekApiKey != null && !platformDeepseekApiKey.isBlank() && hasUrl(deepseekBaseUrl);
            case OLLAMA -> hasUrl(ollama(cfg).getBaseUrl());
        };
    }

    private boolean canVision(AiProvidersConfig cfg, AiProviderType p) {
        return switch (p) {
            case GROQ, OPENAI, OLLAMA -> canChatJson(cfg, p);
            case CLAUDE, GEMINI, DEEPSEEK -> false;
        };
    }

    private boolean canTranscribe(AiProvidersConfig cfg, AiProviderType p) {
        return switch (p) {
            case GROQ -> hasKey(groq(cfg)) && hasUrl(groq(cfg).getBaseUrl());
            case OPENAI -> hasKey(openai(cfg)) && hasUrl(openai(cfg).getBaseUrl());
            case CLAUDE, GEMINI, DEEPSEEK -> false;
            case OLLAMA -> hasUrl(ollama(cfg).getBaseUrl());
        };
    }

    private String chatModelFor(AiProviderType p, AiProvidersConfig cfg) {
        return switch (p) {
            case GROQ -> groq(cfg).getModelText();
            case OPENAI -> openai(cfg).getModel();
            case CLAUDE -> claudeModel != null && !claudeModel.isBlank() ? claudeModel : "claude-3-5-haiku-20241022";
            case GEMINI -> geminiModel;
            case DEEPSEEK -> deepseekModel != null && !deepseekModel.isBlank() ? deepseekModel : "deepseek-chat";
            case OLLAMA -> ollama(cfg).getModel();
        };
    }

    private String visionModelFor(AiProviderType p, AiProvidersConfig cfg) {
        return switch (p) {
            case GROQ -> groq(cfg).getModelVision();
            case CLAUDE -> claudeModel;
            case GEMINI -> geminiModel;
            case DEEPSEEK -> deepseekModel;
            case OPENAI -> {
                String m = openai(cfg).getModel();
                if (m != null && !m.isBlank()
                    && (m.contains("gpt-4") || m.contains("gpt-5") || m.contains("vision") || m.contains("o4"))) {
                    yield m;
                }
                yield "gpt-4o";
            }
            case OLLAMA -> ollama(cfg).getModel();
        };
    }

    private String whisperModelFor(AiProviderType p, AiProvidersConfig cfg) {
        return switch (p) {
            case GROQ -> groq(cfg).getWhisperModel();
            case OPENAI -> openai(cfg).getWhisperModel();
            case CLAUDE, GEMINI, DEEPSEEK -> "";
            case OLLAMA -> ollama(cfg).getModel();
        };
    }

    private String apiKeyFor(AiProviderType p, AiProvidersConfig cfg) {
        return switch (p) {
            case GROQ -> groq(cfg).getApiKey();
            case OPENAI -> openai(cfg).getApiKey();
            case CLAUDE -> platformClaudeApiKey;
            case GEMINI -> platformGeminiApiKey;
            case DEEPSEEK -> platformDeepseekApiKey;
            case OLLAMA -> null;
        };
    }

    private String baseUrlFor(AiProviderType p, AiProvidersConfig cfg) {
        return switch (p) {
            case GROQ -> groq(cfg).getBaseUrl();
            case OPENAI -> openai(cfg).getBaseUrl();
            case CLAUDE -> claudeBaseUrl;
            case GEMINI -> geminiBaseUrl;
            case DEEPSEEK -> deepseekBaseUrl;
            case OLLAMA -> ollama(cfg).getBaseUrl();
        };
    }

    private GroqSection groq(AiProvidersConfig cfg) {
        return cfg.getGroq() != null ? cfg.getGroq() : new GroqSection();
    }

    private OpenaiSection openai(AiProvidersConfig cfg) {
        return cfg.getOpenai() != null ? cfg.getOpenai() : new OpenaiSection();
    }

    private OllamaSection ollama(AiProvidersConfig cfg) {
        return cfg.getOllama() != null ? cfg.getOllama() : new OllamaSection();
    }

    private static boolean hasKey(OpenaiSection o) {
        return o.getApiKey() != null && !o.getApiKey().isBlank();
    }

    private static boolean hasKey(GroqSection g) {
        return g.getApiKey() != null && !g.getApiKey().isBlank();
    }

    private static boolean hasUrl(String u) {
        return u != null && !u.isBlank();
    }

    private String transcribeForProvider(AiProviderType provider, AiProvidersConfig cfg, byte[] audioBytes, String filename) {
        String key = apiKeyFor(provider, cfg);
        String baseUrl = baseUrlFor(provider, cfg);
        String model = whisperModelFor(provider, cfg);
        ensureBaseUrl(provider.name(), baseUrl);
        if (provider != AiProviderType.OLLAMA && (key == null || key.isBlank())) {
            throw new RuntimeException(provider.name() + "_API_KEY não configurada");
        }
        HttpHeaders headers = new HttpHeaders();
        if (key != null && !key.isBlank()) {
            headers.setBearerAuth(key);
        }
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ByteArrayResource audioResource = new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("model", model);
        body.add("response_format", "text");
        body.add("file", audioResource);

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.exchange(
            trimTrailingSlash(baseUrl) + "/audio/transcriptions",
            HttpMethod.POST,
            entity,
            String.class
        );

        String raw = response.getBody();
        if (raw == null || raw.isBlank()) {
            throw new RuntimeException(provider.name() + " retornou transcrição vazia");
        }
        log.info("Transcrição concluída via {}", provider.name());
        return raw.trim();
    }

    private void appendMissingPlatformProviders(AiProvidersConfig cfg, List<String> errors) {
        if (!canChatJson(cfg, AiProviderType.GEMINI)) {
            errors.add("GEMINI: GEMINI_API_KEY não configurada no servidor");
        }
        if (!canChatJson(cfg, AiProviderType.CLAUDE)) {
            errors.add("CLAUDE: CLAUDE_API_KEY não configurada no servidor");
        }
        if (!canChatJson(cfg, AiProviderType.DEEPSEEK)) {
            errors.add("DEEPSEEK: DEEPSEEK_API_KEY não configurada no servidor");
        }
    }

    private JsonNode parseChatJsonForProvider(AiProviderType provider, AiProvidersConfig cfg, String model,
                                               String systemPrompt, String userPrompt) {
        return switch (provider) {
            case GEMINI -> parseGeminiJson(model, systemPrompt, userPrompt);
            case CLAUDE -> parseClaudeJson(model, systemPrompt, userPrompt);
            default -> parseCommandOpenAiCompatible(provider.name(), apiKeyFor(provider, cfg), baseUrlFor(provider, cfg),
                model, systemPrompt, userPrompt);
        };
    }

    private JsonNode parseClaudeJson(String model, String systemPrompt, String userPrompt) {
        if (platformClaudeApiKey == null || platformClaudeApiKey.isBlank()) {
            throw new RuntimeException("CLAUDE_API_KEY não configurada");
        }
        ensureBaseUrl("CLAUDE", claudeBaseUrl);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("max_tokens", 8192);
        payload.put("system", systemPrompt);
        payload.put("messages", List.of(Map.of("role", "user", "content", userPrompt)));

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", platformClaudeApiKey.trim());
        headers.set("anthropic-version", "2023-06-01");
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        String url = trimTrailingSlash(claudeBaseUrl) + "/v1/messages";
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            String content = root.path("content").path(0).path("text").asText("");
            if (content.isBlank()) {
                throw new RuntimeException("CLAUDE retornou conteúdo vazio");
            }
            log.info("IA processada via CLAUDE (json)");
            return objectMapper.readTree(stripJsonFence(content).getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Falha ao interpretar resposta JSON de CLAUDE", e);
        }
    }

    private JsonNode parseGeminiJson(String model, String systemPrompt, String userPrompt) {
        if (platformGeminiApiKey == null || platformGeminiApiKey.isBlank()) {
            throw new RuntimeException("GEMINI_API_KEY não configurada");
        }
        ensureBaseUrl("GEMINI", geminiBaseUrl);

        Map<String, Object> payload = Map.of(
            "systemInstruction", Map.of(
                "parts", List.of(Map.of("text", systemPrompt))
            ),
            "contents", List.of(
                Map.of(
                    "role", "user",
                    "parts", List.of(Map.of("text", userPrompt))
                )
            ),
            "generationConfig", Map.of(
                "temperature", 0.1,
                "responseMimeType", "application/json"
            )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        String url = trimTrailingSlash(geminiBaseUrl) + "/models/" + model + ":generateContent?key=" + platformGeminiApiKey.trim();
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        return extractJsonFromGeminiResponse(response.getBody());
    }

    private JsonNode parseCommandOpenAiCompatible(String providerName, String key, String providerBaseUrl, String model,
                                                    String systemPrompt, String userPrompt) {
        ensureOpenAiCompatibleConfigured(providerName, key, providerBaseUrl);
        boolean ollama = AiProviderType.OLLAMA.name().equalsIgnoreCase(providerName);
        Map<String, Object> payload = buildChatJsonPayload(model, systemPrompt, userPrompt, !ollama);
        ResponseEntity<String> response = callOpenAiCompatible(providerBaseUrl, key, payload);
        return extractJsonFromOpenAiCompatibleResponse(response.getBody(), providerName, "comando");
    }

    private JsonNode parseVisionOpenAiCompatible(String providerName, String key, String providerBaseUrl, String model,
                                                 String systemPrompt, String userPrompt, String imageSource) {
        ensureOpenAiCompatibleConfigured(providerName, key, providerBaseUrl);
        boolean ollama = AiProviderType.OLLAMA.name().equalsIgnoreCase(providerName);
        Map<String, Object> payload = buildVisionJsonPayload(model, systemPrompt, userPrompt, imageSource, !ollama);
        ResponseEntity<String> response = callOpenAiCompatible(providerBaseUrl, key, payload);
        return extractJsonFromOpenAiCompatibleResponse(response.getBody(), providerName, "ocr");
    }

    /** Ollama: sem {@code response_format} (mais estável); outros provedores usam JSON mode OpenAI. */
    private static Map<String, Object> buildChatJsonPayload(
        String model, String systemPrompt, String userPrompt, boolean useOpenAiJsonMode
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("temperature", 0.1);
        if (useOpenAiJsonMode) {
            payload.put("response_format", Map.of("type", "json_object"));
        }
        payload.put(
            "messages",
            List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
            )
        );
        return payload;
    }

    private static Map<String, Object> buildVisionJsonPayload(
        String model, String systemPrompt, String userPrompt, String imageSource, boolean useOpenAiJsonMode
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("temperature", 0.1);
        if (useOpenAiJsonMode) {
            payload.put("response_format", Map.of("type", "json_object"));
        }
        payload.put(
            "messages",
            List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of(
                    "role", "user",
                    "content", List.of(
                        Map.of("type", "text", "text", userPrompt),
                        Map.of("type", "image_url", "image_url", Map.of("url", imageSource))
                    )
                )
            )
        );
        return payload;
    }

    private ResponseEntity<String> callOpenAiCompatible(String providerBaseUrl, String key, Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        if (key != null && !key.isBlank()) {
            headers.setBearerAuth(key);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        return restTemplate.exchange(trimTrailingSlash(providerBaseUrl) + "/chat/completions", HttpMethod.POST, entity, String.class);
    }

    private JsonNode extractJsonFromOpenAiCompatibleResponse(String body, String providerName, String operation) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String content = stripJsonFence(root.path("choices").path(0).path("message").path("content").asText(""));
            if (content.isBlank()) {
                throw new RuntimeException(providerName + " retornou " + operation + " vazio");
            }
            log.info("IA processada via {} ({})", providerName, operation);
            return objectMapper.readTree(content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Falha ao interpretar resposta " + operation + " de " + providerName, e);
        }
    }

    private JsonNode extractJsonFromGeminiResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String content = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
            if (content.isBlank()) {
                throw new RuntimeException("GEMINI retornou JSON vazio");
            }
            log.info("IA processada via GEMINI (json)");
            return objectMapper.readTree(stripJsonFence(content).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Falha ao interpretar resposta JSON de GEMINI", e);
        }
    }

    private static String stripJsonFence(String content) {
        String s = content == null ? "" : content.trim();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```(?:json)?\\s*", "");
            s = s.replaceFirst("\\s*```$", "");
        }
        return s.trim();
    }

    private void ensureOpenAiCompatibleConfigured(String providerName, String key, String providerBaseUrl) {
        if (!"OLLAMA".equals(providerName) && (key == null || key.isBlank())) {
            throw new RuntimeException(providerName + "_API_KEY não configurada");
        }
        if (providerBaseUrl == null || providerBaseUrl.isBlank()) {
            throw new RuntimeException(providerName + "_BASE_URL não configurada");
        }
    }

    private void ensureBaseUrl(String providerName, String providerBaseUrl) {
        if (providerBaseUrl == null || providerBaseUrl.isBlank()) {
            throw new RuntimeException(providerName + "_BASE_URL não configurada");
        }
    }

    private static String trimTrailingSlash(String url) {
        if (url == null) {
            return null;
        }
        String u = url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    /** Config da BD + chave Groq da plataforma (variável consumoesperto.ai.platform-groq-api-key / GROQ_API_KEY). */
    private AiProvidersConfig cfgForAi(Long userId) {
        AiProvidersConfig cfg = aiProvidersConfigService.load(userId);
        aiProvidersConfigService.applyGroqMasterFallback(cfg);
        aiProvidersConfigService.applyOpenaiMasterFallback(cfg);
        aiProvidersConfigService.applyOllamaMasterFallback(cfg);
        cfg.setProviderOrder(AiProviderOrder.canonicalNamesCopy());
        return cfg;
    }
}
