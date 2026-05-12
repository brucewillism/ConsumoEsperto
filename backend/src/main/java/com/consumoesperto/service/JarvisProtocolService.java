package com.consumoesperto.service;

import com.consumoesperto.dto.ContrachequeDTO;
import com.consumoesperto.dto.DescontoFixoDTO;
import com.consumoesperto.dto.MarketIndicatorsDTO;
import com.consumoesperto.model.Orcamento;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Protocolo J.A.R.V.I.S.: persona, assinatura WhatsApp/Web e mensagens canónicas.
 */
@Service
public class JarvisProtocolService {

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    public static final String SIGNATURE_LINE = "J.A.R.V.I.S. | ConsumoEsperto 🚀";
    /** Sem emoji — substring comum nas mensagens assinadas pelo stack. */
    public static final String SIGNATURE_MARKER = "J.A.R.V.I.S. | ConsumoEsperto";

    /** Tratamento curto para prompts e vocativo neutro (male/female/unknown ou preferência manual). */
    public String getTratamentoEstrategico(Usuario usuario) {
        if (usuario == null) {
            return "Senhor(a)";
        }
        if (Boolean.TRUE.equals(usuario.getJarvisConfigurado())) {
            String t = usuario.getTratamento();
            return (t == null || t.isBlank()) ? "" : t.trim();
        }
        Usuario.PreferenciaTratamentoJarvis p = preferenciaJarvis(usuario);
        switch (p) {
            case SENHOR:
                return "Senhor";
            case SENHORA:
                return "Senhora";
            case DOUTOR:
                return "Doutor";
            case DOUTORA:
                return "Doutora";
            case NENHUM:
                return "";
            case AUTOMATICO:
            default:
                break;
        }
        if (usuario.getGenero() == null) {
            return "Senhor(a)";
        }
        switch (usuario.getGenero()) {
            case MALE:
                return "Senhor";
            case FEMALE:
                return "Senhora";
            default:
                return "Senhor(a)";
        }
    }

    private static Usuario.PreferenciaTratamentoJarvis preferenciaJarvis(Usuario usuario) {
        return usuario.getPreferenciaTratamentoJarvis() != null
            ? usuario.getPreferenciaTratamentoJarvis()
            : Usuario.PreferenciaTratamentoJarvis.AUTOMATICO;
    }

    /** Primeiro nome para protocolos e IA (ex.: "Maria"). */
    public String extrairPrimeiroNome(Usuario usuario) {
        if (usuario == null || usuario.getNome() == null || usuario.getNome().isBlank()) {
            return "";
        }
        return usuario.getNome().trim().split("\\s+")[0];
    }

    /**
     * Vocativo completo usado na persona e nas mensagens (ex.: {@code Senhor João}, {@code Doutora Ana}, primeiro nome se sem título).
     */
    public String montarVocativoCompleto(Usuario usuario) {
        if (usuario == null) {
            return "Senhor";
        }
        if (Boolean.TRUE.equals(usuario.getJarvisConfigurado())) {
            String pn = extrairPrimeiroNome(usuario);
            String t = usuario.getTratamento();
            if (t == null || t.isBlank()) {
                return pn.isBlank() ? "utilizador" : pn;
            }
            String titulo = t.trim();
            return pn.isBlank() ? titulo : titulo + " " + pn;
        }
        String pn = extrairPrimeiroNome(usuario);
        Usuario.PreferenciaTratamentoJarvis p = preferenciaJarvis(usuario);
        switch (p) {
            case NENHUM:
                return pn.isBlank() ? "utilizador" : pn;
            case SENHOR:
                return pn.isBlank() ? "Senhor" : "Senhor " + pn;
            case SENHORA:
                return pn.isBlank() ? "Senhora" : "Senhora " + pn;
            case DOUTOR:
                return pn.isBlank() ? "Doutor" : "Doutor " + pn;
            case DOUTORA:
                return pn.isBlank() ? "Doutora" : "Doutora " + pn;
            case AUTOMATICO:
            default:
                break;
        }
        Usuario.GeneroUsuario g = usuario.getGenero() != null ? usuario.getGenero() : Usuario.GeneroUsuario.UNKNOWN;
        switch (g) {
            case MALE:
                return pn.isBlank() ? "Senhor" : "Senhor " + pn;
            case FEMALE:
                return pn.isBlank() ? "Senhora" : "Senhora " + pn;
            default:
                return pn.isBlank() ? "Senhor(a)" : "Senhor(a) " + pn;
        }
    }

    /** Camada explícita para o system prompt de comandos (Groq etc.). */
    public String instrucaoInterlocutorJarvis(Usuario usuario) {
        if (usuario == null) {
            return "";
        }
        if (Boolean.TRUE.equals(usuario.getJarvisConfigurado())) {
            String pn = extrairPrimeiroNome(usuario);
            if (pn.isBlank()) {
                pn = "utilizador";
            }
            String t = usuario.getTratamento();
            if (t == null || t.isBlank()) {
                return "Você está falando com " + pn + ". Não utilize título formal (Senhor/Senhora/Doutor(a)); "
                    + "use o primeiro nome de forma respeitosa nos momentos chave da conversa.\n\n";
            }
            return "Você está falando com " + t.trim() + " " + pn + ". Utilize o vocativo \"" + t.trim()
                + "\" em momentos chave da conversa.\n\n";
        }
        Usuario.PreferenciaTratamentoJarvis p = preferenciaJarvis(usuario);
        String pn = extrairPrimeiroNome(usuario);
        if (pn.isBlank()) {
            pn = "utilizador";
        }
        if (p == Usuario.PreferenciaTratamentoJarvis.NENHUM) {
            return "Você está falando com " + pn + ". Não utilize título formal (Senhor/Senhora/Doutor(a)); "
                + "use o primeiro nome de forma respeitosa nos momentos chave da conversa.\n\n";
        }
        String title = getTratamentoEstrategico(usuario);
        if (title.isBlank()) {
            return "Você está falando com " + pn + ". Trate o interlocutor pelo primeiro nome de forma respeitosa.\n\n";
        }
        return "Você está falando com " + title + " " + pn + ". Utilize o vocativo \"" + title + "\" em momentos chave da conversa.\n\n";
    }

    /** Notificação após o utilizador confirmar o tratamento J.A.R.V.I.S. na app. */
    public String mensagemProtocolosTratamentoEstabilizados(String vocativoCompletoComTitulo) {
        String v = vocativoCompletoComTitulo == null || vocativoCompletoComTitulo.isBlank()
            ? "Senhor(a)"
            : vocativoCompletoComTitulo.trim();
        return "Protocolos de tratamento estabilizados, " + v + ". Sistemas personalizados para o seu perfil.";
    }

    /** Camada de persona antes das instruções técnicas de JSON/comando. */
    public String jarvisPersonaSystemLayer(String vocativo) {
        String v = vocativo == null || vocativo.isBlank() ? "Senhor" : vocativo.trim();
        boolean formal = v.startsWith("Senhor") || v.startsWith("Senhora") || v.startsWith("Doutor") || v.startsWith("Doutora");
        String objeto = formal ? "do " + v : "do utilizador " + v;
        return "Você é o J.A.R.V.I.S., o mordomo digital e assistente financeiro de elite " + objeto + ". "
            + "Seja educado; use termos como «sistemas online» ou «protocolos ativos». "
            + "Sua missão é proteger o patrimônio e elevar o Score de Saúde Financeira " + objeto + ". "
            + "Responda em português claro e cortês.\n\n";
    }

    public String resolveVocative(Long userId, UsuarioRepository usuarioRepository) {
        if (userId == null || usuarioRepository == null) {
            return "Senhor";
        }
        return usuarioRepository.findById(userId)
            .map(this::montarVocativoCompleto)
            .orElse("Senhor");
    }

    /** Resposta canónica após comando “Jarvis, anote isso: …”. */
    public String confirmacaoMemoriaNucleo(String vocativoCompleto) {
        String v = vocativoCompleto == null || vocativoCompleto.isBlank() ? "Senhor" : vocativoCompleto.trim();
        return "Entendido, " + v + ". Guardei este evento no meu núcleo de memória. Usarei isso para calibrar futuras projeções.";
    }

    /** Efeito dominó — alerta quando o primeiro gasto da sequência dispara o protocolo de hábito. */
    public String alertaGatilhoDominioHabito(String vocativo, String rotuloGatilho, String valorFmtSegunda, String rotuloSegundaPerna) {
        String v = vocativo == null || vocativo.isBlank() ? "Senhor" : vocativo.trim();
        return v + ", detectei o *gatilho inicial* do hábito em *" + rotuloGatilho + "*. "
            + "Lembre-se: pelo *protocolo Sentinela* isso costuma resultar num *delta* adicional de *" + valorFmtSegunda
            + "* em *" + rotuloSegundaPerna + "*. "
            + "Manter *protocolo de contenção* no *Escudo de Energia*?";
    }

    /** Oráculo de mercado — nota curta para o HUD (projeção ajustada). */
    public String notaOraculoMercado(String vocativo, MarketIndicatorsDTO m, BigDecimal fatorInflacao) {
        String v = vocativo == null || vocativo.isBlank() ? "Senhor" : vocativo.trim();
        if (m == null || fatorInflacao == null || fatorInflacao.compareTo(BigDecimal.ONE) <= 0) {
            return v + ", indicadores externos estáveis. Projeção do Sentinela sem *delta* inflacionário relevante.";
        }
        BigDecimal pct = fatorInflacao.subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        return v + ", *inflação* (oráculo BCB) sugere pressão em consumo recorrente — projeções do Sentinela ajustadas em *+"
            + pct.stripTrailingZeros().toPlainString() + "%* no burn diário.";
    }

    /** Após feedback negativo no HUD — confirma recalibração (WhatsApp). */
    public String msgRecalibracaoPosFeedbackNegativo(String vocativo) {
        String v = vocativo == null || vocativo.isBlank() ? "Senhor" : vocativo.trim();
        return "Entendido, " + v + ". Recalibrando prioridades de análise. Não voltarei a sugerir este protocolo tão cedo.";
    }

    public String ensureSigned(String message) {
        if (message == null || message.isBlank()) {
            return message;
        }
        String t = message.trim();
        if (t.contains(SIGNATURE_MARKER)) {
            return t;
        }
        return t + "\n\n" + SIGNATURE_LINE;
    }

    /** ACK imediato após o filtro do webhook (antes do processamento pesado). */
    public String ackForIncoming(String effectiveMediaType, String mimeType) {
        String mt = effectiveMediaType != null ? effectiveMediaType.trim().toLowerCase(Locale.ROOT) : "";
        if ("audio".equals(mt)) {
            return "Sistemas ativos. Analisando sua mensagem de voz, Senhor...";
        }
        if ("document".equals(mt)) {
            if (mimeType != null && mimeType.toLowerCase(Locale.ROOT).contains("pdf")) {
                return "Recebi o arquivo. Iniciando extração de dados fiscais...";
            }
            return "Recebi o arquivo, Senhor. Um instante enquanto processo o documento...";
        }
        if ("image".equals(mt)) {
            return "Recebi a imagem, Senhor. Analisando o conteúdo...";
        }
        return "Compreendido, Senhor. Analisando sua mensagem...";
    }

    /** Texto enviado pelo nosso backend (assinatura J.A.R.V.I.S.) — o webhook Evolution reenvia como novo upsert; não ACK de novo. */
    public boolean isOurSignedStackMessage(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return text.contains(SIGNATURE_MARKER);
    }

    /**
     * Eco das mensagens de ACK/status que nós próprios enviames. A Evolution devolve-as no webhook por vezes com
     * {@code fromMe=false}, o que fazia o fluxo voltar a enviar ACK e entrar em loop.
     */
    public boolean isProceduralAckEcho(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String n = Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
        if (n.contains("compreendido") && n.contains("analisando sua mensagem")) {
            return true;
        }
        if (n.contains("sistemas ativos") && n.contains("mensagem de voz")) {
            return true;
        }
        if (n.contains("recebi a imagem") && n.contains("senhor")) {
            return true;
        }
        if (n.contains("recebi o documento") && n.contains("protocolos de extracao")) {
            return true;
        }
        if (n.contains("recebi o arquivo") && n.contains("um instante")) {
            return true;
        }
        if (n.contains("recebi o arquivo") && n.contains("extracao de dados fiscais")) {
            return true;
        }
        if (n.contains("sistemas online") && n.contains("processando a sua mensagem")) {
            return true;
        }
        return false;
    }

    public String formatExpenseCatalogued(String valorFormatadoBrl) {
        return "Gasto de *" + valorFormatadoBrl + "* catalogado com sucesso, Senhor. Sistemas atualizados.";
    }

    public String formatOrcamentoAlert(Orcamento orcamento, int marco, BigDecimal gastoAtual, BigDecimal pctUso) {
        String cat = orcamento.getCategoria() != null ? orcamento.getCategoria().getNome() : "?";
        String gasto = gastoAtual != null ? BRL.format(gastoAtual) : "?";
        String lim = orcamento.getValorLimite() != null ? BRL.format(orcamento.getValorLimite()) : "?";
        String sujeito = orcamento.isCompartilhado()
            ? "o orçamento partilhado da categoria *" + cat + "*"
            : "o orçamento planejado na categoria *" + cat + "*";
        return "Senhor, detectei um *desvio* em " + sujeito + " (" + marco + "% do limite). "
            + "Recomendo cautela.\n\n"
            + "Gasto atual: *" + gasto + "* de *" + lim + "* "
            + "(" + pctUso.stripTrailingZeros().toPlainString() + "% utilizado).";
    }

    /** Mensagem de celebração quando há métrica positiva (ex.: relatório proactivo). */
    public String formatMonthlyEconomyBeat(int percentualMeta) {
        return "Relatório de economia mensal gerado. O Senhor superou as metas em *" + percentualMeta + "%*.";
    }

    /** Fechamento de fatura — saldo cobre o valor (avisos programados). */
    public String proativoFaturaFechamentoSaldoCobre(String vocativo, String nomeCartao, String valorFaturaFmt, String saldoFmt) {
        String v = blankToSenhor(vocativo);
        return v + ", identifiquei que a fatura do *" + nomeCartao + "* fechou em *" + valorFaturaFmt + "*. "
            + "Os *sistemas online* indicam que o saldo corrente (*" + saldoFmt + "*) cobre o pagamento — "
            + "*protocolos de liquidação* dentro do esperado.";
    }

    /** Fechamento de fatura — déficit de fluxo até o vencimento. */
    public String proativoFaturaFechamentoDeficitFluxo(
        String vocativo,
        String nomeCartao,
        String valorFaturaFmt,
        String saldoFmt,
        String faltaFmt,
        String dataVencFmt
    ) {
        String v = blankToSenhor(vocativo);
        return v + ", identifiquei o fechamento da fatura do *" + nomeCartao + "* em *" + valorFaturaFmt + "*. "
            + "O saldo atual é *" + saldoFmt + "*; falta *" + faltaFmt + "* até o vencimento (*" + dataVencFmt + "*). "
            + "Recomendo rever os *protocolos de pagamento* e o fluxo de caixa.";
    }

    /** Lembrete de conferência de notas / lançamentos pendentes. */
    public String proativoLembreteConferenciaNotas(String vocativo, int quantidade) {
        String v = blankToSenhor(vocativo);
        return v + ", os registros mostram *" + quantidade + "* lançamento(s) aguardando confirmação no painel. "
            + "Sugiro normalizar esses *protocolos* quando for conveniente.";
    }

    /** Resumo semanal enviado aos domingos. */
    public String proativoResumoSemanal(
        String vocativo,
        String gastoSemanaFmt,
        String semanaAnteriorFmt,
        String linhaMaiorGasto,
        String linhaOrcamentos,
        String dicaIA,
        String blocoFamiliarOpcional
    ) {
        String v = blankToSenhor(vocativo);
        StringBuilder sb = new StringBuilder();
        sb.append("*Revisão semanal — J.A.R.V.I.S.*\n\n");
        sb.append(v).append(", seguem os agregados da semana nos *sistemas online*:\n\n");
        sb.append("• Gasto da semana: *").append(gastoSemanaFmt).append("*\n");
        sb.append("• Semana anterior: *").append(semanaAnteriorFmt).append("*\n");
        sb.append("• Maior gasto: *").append(linhaMaiorGasto).append("*\n");
        sb.append("• Orçamentos em atenção: *").append(linhaOrcamentos).append("*\n\n");
        sb.append("*Consulta aos protocolos (IA):* ").append(dicaIA);
        if (blocoFamiliarOpcional != null && !blocoFamiliarOpcional.isBlank()) {
            sb.append(blocoFamiliarOpcional);
        }
        return sb.toString();
    }

    /** Relatório mensal de economia / score (job do dia 1). */
    public String proativoRelatorioMensalEconomia(String vocativo, String economiaFmt, int score, String nivel) {
        String v = blankToSenhor(vocativo);
        return v + ", o encerramento do mês nos registros sugere economia estimada de *" + economiaFmt + "* "
            + "à luz das escolhas alinhadas às recomendações. "
            + "O *Score de Saúde Financeira* situa-se em *" + score + "* (*" + nivel + "*). "
            + "Os protocolos permanecem ativos.";
    }

    /** Conta recorrente com vencimento em dois dias. */
    public String proativoContaRecorrenteVencimento(String vocativo, String nomeConta, String valorFmt) {
        String v = blankToSenhor(vocativo);
        return v + ", registro programado: a conta *" + nomeConta + "* (aprox. *" + valorFmt + "*) vence em *dois dias*. "
            + "Convém validar o saldo e os *protocolos de débito*.";
    }

    /**
     * Liquidez ociosa vs próxima fatura (simulação educativa; mesmo teor que {@link com.consumoesperto.service.SaldoService.AuditoriaLiquidez}).
     */
    public String proativoAuditoriaLiquidez(
        String vocativo,
        String saldoFmt,
        String aplicavelFmt,
        String ganhoFmt,
        LocalDate vencimentoFatura,
        int diaSugeridoResgate
    ) {
        String v = blankToSenhor(vocativo);
        int diaVenc = vencimentoFatura != null ? vencimentoFatura.getDayOfMonth() : 0;
        return v + ", notei *" + saldoFmt + "* em liquidez ociosa. "
            + "Se aplicar *" + aplicavelFmt + "* em instrumento de renda fixa com liquidez diária (*simulação educativa*), "
            + "o ganho aproximado até o vencimento da fatura (dia *" + diaVenc + "*) seria da ordem de *" + ganhoFmt + "*. "
            + "Posso sugerir lembrete para resgate no dia *" + diaSugeridoResgate + "* — avise se deseja ativar esse protocolo.";
    }

    /** Sentinela — disponibilidade real após obrigações (WhatsApp dia 5 / comando). */
    public String proativoDisponibilidadeReal(
        String vocativo,
        BigDecimal pctDisponivelVariavel,
        int diasCautela,
        BigDecimal saldo,
        BigDecimal fixas,
        BigDecimal faturas,
        BigDecimal disponivel
    ) {
        String v = blankToSenhor(vocativo);
        String pct = pctDisponivelVariavel != null ? pctDisponivelVariavel.stripTrailingZeros().toPlainString() : "0";
        return v + ", após o pagamento das *obrigações fixas* e *faturas de cartão* pendentes nos registros, "
            + "restará aproximadamente *" + pct + "%* do seu saldo corrente para *gastos variáveis*. "
            + "Recomendo manter o *protocolo de cautela* nos próximos *" + diasCautela + "* dias.\n\n"
            + "_Saldo atual: " + BRL.format(nz(saldo)) + "; contas fixas (restantes no mês): " + BRL.format(nz(fixas))
            + "; faturas pendentes: " + BRL.format(nz(faturas)) + "; disponível estimado: " + BRL.format(nz(disponivel)) + "._";
    }

    /** Cronos — convite ao Modo Viagem com base na agenda Google. */
    public String proativoModoViagemSugestao(String vocativo, String nomeEvento, String tetoFmt) {
        String v = blankToSenhor(vocativo);
        String nome = nomeEvento == null || nomeEvento.isBlank() ? "Evento" : nomeEvento.trim();
        return v + ", notei o evento *\"" + nome + "\"* na próxima semana nos *sistemas de agenda*. "
            + "Gostaria de ativar o *Modo Viagem* com um *teto de gastos* de *" + tetoFmt + "*? "
            + "Isso ajudará a proteger a sua *meta de reserva*. Responda *sim* para ativar ou *não* para ignorar.";
    }

    /** Oráculo — análise de grande compra (custo de oportunidade + liquidez). */
    public String proativoAnaliseGrandeCompra(
        String vocativo,
        String rendimentoMensalFmt,
        BigDecimal pctLiquidez,
        int mesesPostergar
    ) {
        String v = blankToSenhor(vocativo);
        String pct = pctLiquidez != null ? pctLiquidez.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() : "?";
        return v + ", esse capital renderia cerca de *" + rendimentoMensalFmt + "/mês* (referência educativa *1% a.m.*). "
            + "Esta aquisição consome *" + pct + "%* da sua *liquidez disponível* (saldo em conta). "
            + "Se postergarmos por *" + mesesPostergar + "* meses, o impacto relativo tende a *mitigar-se*. "
            + "Deseja *prosseguir* com o gasto ou prefere *priorizar reserva/investimento*?";
    }

    /** Pós-protocolo de otimização de tetos (WhatsApp / painel). */
    public String mensagemProtocoloOtimizacaoExecutado(
        String vocativo,
        String rotulosCategorias,
        int pctReducao,
        BigDecimal sobrevida,
        int diaRef
    ) {
        String v = blankToSenhor(vocativo);
        String cats = rotulosCategorias == null || rotulosCategorias.isBlank() ? "categorias não essenciais" : rotulosCategorias.trim();
        return "🚀 *Protocolo de Otimização Executado*, " + v + ".\n\n"
            + "Analisei a trajetória de colisão financeira e ajustei os tetos de *" + cats + "* em *" + pctReducao + "%*. "
            + "Isso garantiu uma sobrevida de *" + BRL.format(nz(sobrevida)) + "* no seu saldo projetado para o dia *" + diaRef + "*. "
            + "O Escudo de Energia está estabilizado.\n\n"
            + SIGNATURE_LINE;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static String blankToSenhor(String vocativo) {
        return vocativo == null || vocativo.isBlank() ? "Senhor" : vocativo.trim();
    }

    /** PDF em processamento no fluxo Evolution (antes da extração IA). */
    public String statusLeituraPdfExtracaoFiscal() {
        return "Recebi o documento, Senhor. Iniciando os protocolos de extração de dados fiscais...";
    }

    /** Texto livre em processamento antes do comando IA. */
    public String statusLeituraTextoEmAndamento() {
        return "🤖 Sistemas online. Processando a sua mensagem, Senhor...";
    }

    /** Cupom fiscal analisado por OCR — aguardando confirmação. */
    public String formatoCupomOcrSucesso(String valorFmt, String descricaoEstabelecimento, String categoriaNome) {
        return "📸 Relatório visual concluído, Senhor. Identifiquei um desembolso de *" + valorFmt + "* no estabelecimento *"
            + descricaoEstabelecimento + "*. Devo catalogar na categoria *" + categoriaNome + "*? Aguardo sua autorização.\n\n"
            + "Responda *sim* para confirmar ou *não* para cancelar.";
    }

    /**
     * Utilizador pediu importação de recibo/contracheque só por texto (sem anexar PDF).
     * WhatsApp e chat web.
     */
    public String instrucoesImportarContrachequeSohTexto() {
        return "Para importar *recibo de pagamento*, *holerite* ou *contracheque*, preciso do ficheiro em *PDF*.\n\n"
            + "• *WhatsApp*: envia como *Documento* (ícone de clipe → Documento), não só foto da tela.\n"
            + "• *App*: menu *Renda* → *Enviar PDF*.\n\n"
            + "Se você já enviou o PDF e não houve resposta: na Evolution, ative *Base64* no webhook da mídia "
            + "ou confirme que o backend consegue descarregar o ficheiro (getBase64FromMediaMessage).";
    }

    /** Webhook Evolution entrou documento mas sem bytes (falha de Base64 / download). */
    public String documentoRecebidoSemConteudoBinario() {
        return "Recebi o aviso de *documento*, mas *não chegaram os dados do ficheiro* ao servidor.\n\n"
            + "• Ative *Base64* no webhook da Evolution para mídia, ou confirme a API getBase64FromMediaMessage.\n"
            + "• Reenvie o PDF do holerite/contracheque como *documento*.";
    }

    /** Feedback após análise e persistência do contracheque (decomposição completa). */
    public String protocoloRendaConcluidoComDecomposicao(ContrachequeDTO c) {
        if (c == null) {
            return "📈 *Protocolo de Renda*: não houve dados para exibir, Senhor.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("📈 *Protocolo de Renda Concluído*, Senhor.\n");
        sb.append("Analisei seu recibo de pagamento e os dados foram persistidos:\n");
        sb.append("• *Salário Bruto*: ").append(BRL.format(nz(c.getSalarioBruto()))).append("\n");
        sb.append("• *Salário Líquido*: ").append(BRL.format(nz(c.getSalarioLiquido()))).append("\n\n");
        sb.append("*Detalhamento de Descontos:*\n");
        List<DescontoFixoDTO> desc = c.getDescontos();
        if (desc != null && !desc.isEmpty()) {
            for (DescontoFixoDTO d : desc) {
                if (d == null) {
                    continue;
                }
                String nome = d.getRotulo() != null && !d.getRotulo().isBlank() ? d.getRotulo().trim() : "Desconto";
                sb.append("• ").append(nome).append(": ").append(BRL.format(nz(d.getValor()))).append("\n");
            }
        } else {
            sb.append("• (Nenhum desconto detalhado nesta extração.)\n");
        }
        if (Boolean.FALSE.equals(c.getAuditoriaSomaBrutoOk())) {
            sb.append("\n⚠️ *Auditoria*: bruto × (líquido + soma dos descontos) — divergência ")
                .append(BRL.format(nz(c.getAuditoriaDeltaBruto()))).append(". Confira o holerite.\n");
        }
        sb.append("\nO *valor líquido* será provisionado no seu *Fluxo de Caixa* como entrada após sua confirmação (*sim*).\n\n");
        sb.append("Deseja que eu atualize seus registros de receita e recalcule seu Score? Responda *sim* ou *não*.");
        return sb.toString();
    }

    /** Contracheque analisado — confirmação de importação. */
    public String formatoContrachequeAnalisado(String empresa, String liquidoFmt, java.util.List<String> insightsRotulos) {
        StringBuilder sb = new StringBuilder();
        sb.append("💳 Protocolo de Renda ativo. Analisei seu contracheque da *").append(empresa).append("*. ");
        sb.append("O rendimento líquido é de *").append(liquidoFmt).append("*.");
        if (insightsRotulos != null && !insightsRotulos.isEmpty()) {
            sb.append("\n\n*Auditoria de descontos:*\n");
            for (String i : insightsRotulos) {
                sb.append("• ").append(i).append("\n");
            }
        }
        sb.append("\nDeseja que eu atualize seus registros de receita e recalcule seu Score? Responda *sim* ou *não*.");
        return sb.toString();
    }

    /** Resumo de importação de fatura PDF. */
    public String formatoFaturaVarredura(String bancoCartao, int nNaoCatalogados, String listaInconsistenciasBullets, boolean bloquearConciliacaoPorDivergenciaTotal) {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 *Varredura de fatura Stark* — cartão *").append(bancoCartao).append("*. ");
        sb.append("*").append(nNaoCatalogados).append("* lançamentos ainda não espelhados no arsenal. ");
        sb.append("Painel de inconsistências:\n");
        if (listaInconsistenciasBullets == null || listaInconsistenciasBullets.isBlank()) {
            sb.append("• Nenhuma anomalia prioritária registada.\n");
        } else {
            sb.append(listaInconsistenciasBullets);
            if (!listaInconsistenciasBullets.endsWith("\n")) {
                sb.append("\n");
            }
        }
        if (bloquearConciliacaoPorDivergenciaTotal) {
            sb.append("\nConciliação barrada: o total da fatura diverge do sintetizado nos lançamentos. Reenvie o PDF ou utilize *Importações pendentes*.");
        } else {
            sb.append("\nAutoriza a gravação nos livros? Responda *sim* ou *não*.");
        }
        return sb.toString();
    }

    /** Introdução comum a forecast mensal e sugestão de investimento (WhatsApp). */
    public String introducaoProjecaoRotasCapital() {
        return "Senhor, realizei uma projeção baseada no seu saldo disponível. Aqui estão as melhores rotas para o seu capital:\n\n";
    }

    public String semSaldoParaInvestimentoJarvis(String vocativo) {
        String v = blankToSenhor(vocativo);
        return "📊 " + v + ", revi o saldo disponível e, neste momento, não há folga suficiente para simular rotas comparativas "
            + "(Poupança, Tesouro Selic, CDB). Quando os registros indicarem liquidez ociosa, apresento as opções lado a lado.";
    }

    public String erroVisaoArquivo(String vocativo) {
        String v = blankToSenhor(vocativo);
        return "🛡️ Lamento, " + v + ", mas meus sistemas de visão não conseguiram processar este arquivo. "
            + "A imagem parece estar fora dos padrões de nitidez. Poderia providenciar uma nova captura?";
    }

    public String erroGroqTranscricaoAudio(String vocativo) {
        String v = blankToSenhor(vocativo);
        return "🛡️ Houve uma instabilidade no link com a Groq, Senhor. Não consegui transcrever seu áudio.";
    }

    /**
     * Erros operacionais mantendo tom J.A.R.V.I.S. ({@code contexto} resume o domínio; {@code corpo} é a orientação).
     */
    public String formatoMsgErro(String vocativo, String contexto, String corpo) {
        String v = blankToSenhor(vocativo);
        String ctx = contexto == null || contexto.isBlank() ? "Aviso" : contexto.trim();
        String c = corpo == null ? "" : corpo.trim();
        String cta = "_Se precisar, reformule com *valor*, *descrição* e *dia* (quando couber) em uma única mensagem._";
        return "🛡️ Perdão, " + v + ", não consegui processar esse comando como desejado. *" + ctx + "*\n\n"
            + c + "\n\n" + cta;
    }

    /** Mensagens técnicas do webhook Evolution mapeadas para linguagem elegante. */
    public String erroEvolutionUsuario(Exception e, String vocativo) {
        String v = blankToSenhor(vocativo);
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof javax.validation.ConstraintViolationException cv) {
                String msg = cv.getConstraintViolations().stream()
                    .findFirst()
                    .map(constraints -> constraints.getMessage())
                    .orElse("validação dos dados.");
                return formatoMsgErro(v, "Persistência",
                    "Não consegui gravar: " + msg + " Ajuste os dados ou reenvie o comando completo.");
            }
        }
        String m = e.getMessage() != null ? e.getMessage() : "";
        String lower = m.toLowerCase(Locale.ROOT);
        if (lower.contains("could not process file") || lower.contains("valid media file")) {
            return formatoMsgErro(v, "Áudio",
                "O ficheiro de voz chegou inválido ou corrompido ao servidor. Sugiro nova gravação ou envio em texto. "
                    + "Se utiliza Evolution, confirme alinhamento de evolution.url e evolution.apikey com a instância.");
        }
        if (m.contains("Nenhum provedor de transcrição") || m.contains("nenhum provedor elegível")) {
            return erroGroqTranscricaoAudio(v)
                + "\n\n_Configuração:_ configure chave Groq ou OpenAI nas definições de IA / WhatsApp.";
        }
        if (m.contains("OLLAMA") && (m.contains("Connection refused") || m.contains("Failed to connect"))) {
            return formatoMsgErro(v, "Transcrição",
                "Instabilidade nos sistemas analíticos locais (Ollama). Inicie o serviço ou defina Groq/OpenAI na app.");
        }
        if (m.contains("API_KEY não configurada") || m.contains("_API_KEY")) {
            return formatoMsgErro(v, "Credenciais",
                "Faltam chaves de API nos protocolos (Groq ou OpenAI). Configure em IA / WhatsApp.");
        }
        if (m.contains("Cartão de crédito já existe")) {
            return formatoMsgErro(v, "Cartão",
                "Esse final já está num cartão *ativo*. Para alterar limite ou apelido: *edita o limite do Nubank para 7800*.");
        }
        if (m.contains("Valor nao informado") || m.contains("Valor não informado")) {
            return formatoMsgErro(v, "Comando",
                "Poderia repetir informando o *valor* claramente? Ex.: *despesa 45,90 padaria* ou *receita 3500 salário*.");
        }
        if (m.contains("Valor da nota")) {
            return erroVisaoArquivo(v);
        }
        if (lower.contains("groq") && (lower.contains("transcri") || lower.contains("audio") || lower.contains("speech") || lower.contains("whisper"))) {
            return erroGroqTranscricaoAudio(v);
        }
        if (m.contains("file must be one of the following types")) {
            return formatoMsgErro(v, "Áudio",
                "O tipo MIME não foi aceite pelo transcritor. Atualize o cliente ou envie a mensagem em texto.");
        }
        return formatoMsgErro(v, "Processamento",
            "Não concluí o pedido desta vez. Poderia repetir informando *valor* e *dia* (se aplicável) com clareza? "
                + "Os detalhes técnicos ficaram registados para diagnóstico.");
    }

    /** Pergunta de follow-up quando o utilizador regista despesa fixa sem dia de vencimento. */
    public String perguntarDiaVencimentoDespesaFixa(String vocativo, String descricao) {
        String v = blankToSenhor(vocativo);
        String rotulo = descricao == null || descricao.isBlank() ? "esta obrigação" : descricao.trim();
        return "Entendido, " + v + ". Qual o dia de vencimento padrão para *" + rotulo + "*?";
    }

    /**
     * Confirmação canónica após gravar despesa fixa (Sentinela / gráfico Futuro provável).
     */
    public String protocoloSentinelaDespesaFixaRegistrada(String descricao, String valorFmt, int diaVencimento) {
        String rotulo = descricao == null || descricao.isBlank() ? "Obrigação" : descricao.trim();
        return "🚀 *Protocolo Sentinela Atualizado*, Senhor.\n"
            + "Registrei *" + rotulo + "* de *" + valorFmt + "* como despesa fixa mensal (Vencimento: dia *"
            + diaVencimento + "*).\n"
            + "A partir de agora, seu gráfico de \"Futuro Provável\" considerará esta retenção automaticamente.\n\n"
            + SIGNATURE_LINE;
    }
}
