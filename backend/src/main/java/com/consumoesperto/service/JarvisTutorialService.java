package com.consumoesperto.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Tutorial interativo opt-in do J.A.R.V.I.S. (WhatsApp e chat web).
 * Estado em memória por {@code userId} — mesmo padrão dos rascunhos {@code awaiting*} no pipeline Jarvis.
 */
@Service
public class JarvisTutorialService {

    private static final Pattern CAPITULO_NUMERO = Pattern.compile("^[1-5]$");

    private final Map<Long, TutorialSessao> sessoes = new ConcurrentHashMap<>();
    private final SaudacaoService saudacaoService;

    public JarvisTutorialService(SaudacaoService saudacaoService) {
        this.saudacaoService = saudacaoService;
    }

    public boolean isEmTutorial(Long userId) {
        return userId != null && sessoes.getOrDefault(userId, new TutorialSessao()).isEmTutorial();
    }

    public int getCapituloAtual(Long userId) {
        if (userId == null) {
            return 0;
        }
        return sessoes.getOrDefault(userId, new TutorialSessao()).getCapituloAtual();
    }

    /**
     * Roteamento do tutorial. Retorna resposta quando o tutorial consome a mensagem;
     * vazio quando o pipeline normal deve continuar.
     */
    public Optional<String> tryHandle(Long userId, String sourceText, Supplier<String> processarComandoNormal) {
        if (userId == null || sourceText == null) {
            return Optional.empty();
        }
        String text = sourceText.trim();
        if (text.isBlank()) {
            return Optional.empty();
        }

        TutorialSessao sessao = sessoes.computeIfAbsent(userId, id -> new TutorialSessao());
        String norm = normalizar(text);

        if (sessao.isEmTutorial() && isPararTutorial(norm)) {
            encerrar(sessao);
            return Optional.of(getMensagemEncerramento());
        }

        if (sessao.isEmTutorial() && isPassoCapitulo(norm)) {
            int capitulo = Integer.parseInt(norm);
            sessao.setCapituloAtual(capitulo);
            return Optional.of(getCapitulo(capitulo));
        }

        if (sessao.isEmTutorial() && saudacaoService.isSaudacaoIsolada(text)) {
            String resposta = saudacaoService.gerarResposta(
                saudacaoService.extrairSaudacaoUsada(saudacaoService.normalizarParaDetecao(text)), userId);
            return Optional.of(resposta + saudacaoService.lembreteTutorialAtivo());
        }

        if (isIniciarTutorial(norm) || extrairCapituloDaMensagem(norm) > 0) {
            int capituloDireto = extrairCapituloDaMensagem(norm);
            iniciar(sessao);
            if (capituloDireto > 0) {
                sessao.setCapituloAtual(capituloDireto);
                return Optional.of(getCapitulo(capituloDireto));
            }
            return Optional.of(getMenuInicial());
        }

        if (sessao.isEmTutorial()) {
            encerrar(sessao);
            String resposta = processarComandoNormal.get();
            String corpo = resposta != null ? resposta : "";
            return Optional.of("_(Tutorial encerrado automaticamente para processar seu comando.)_\n\n" + corpo);
        }

        return Optional.empty();
    }

    public String responderAcaoTutorial(String action, Long userId, String sourceText, Supplier<String> processarComandoNormal) {
        return switch (action) {
            case "START_TUTORIAL" -> iniciarTutorial(userId, sourceText, 0);
            case "STOP_TUTORIAL" -> {
                encerrar(sessoes.computeIfAbsent(userId, id -> new TutorialSessao()));
                yield getMensagemEncerramento();
            }
            case "TUTORIAL_STEP" -> {
                TutorialSessao s = sessoes.computeIfAbsent(userId, id -> new TutorialSessao());
                if (!s.isEmTutorial()) {
                    iniciar(s);
                }
                int cap = extrairCapituloDeTexto(sourceText);
                if (cap < 1 || cap > 5) {
                    yield getMenuInicial();
                }
                s.setCapituloAtual(cap);
                yield getCapitulo(cap);
            }
            default -> processarComandoNormal.get();
        };
    }

    /** Inicia o guia; {@code capituloSugerido} 1–5 (ex.: reportMonth da IA) ou detecção no texto. */
    public String iniciarTutorial(Long userId, String sourceText, int capituloSugerido) {
        TutorialSessao s = sessoes.computeIfAbsent(userId, id -> new TutorialSessao());
        iniciar(s);
        int cap = capituloSugerido >= 1 && capituloSugerido <= 5
            ? capituloSugerido
            : extrairCapituloDaMensagem(normalizar(sourceText == null ? "" : sourceText.trim()));
        if (cap > 0) {
            s.setCapituloAtual(cap);
            return getCapitulo(cap);
        }
        return getMenuInicial();
    }

    public void encerrarUsuario(Long userId) {
        if (userId == null) {
            return;
        }
        sessoes.remove(userId);
    }

    public String getMenuInicial() {
        return """
            📖 *COMO USAR O CONSUMO ESPERTO*

            Chefe, vou te explicar como funciona cada tela do sistema para você dominar suas finanças. Escolha o número da aba que quer entender:

            *1️⃣* — Aba Contas (Onde seu dinheiro mora)
            *2️⃣* — Aba Transações (Entradas e saídas do dia a dia)
            *3️⃣* — Aba Cartões e Faturas (Limite e boletos)
            *4️⃣* — Aba Renda (Seu perfil financeiro)
            *5️⃣* — Aba Orçamentos e Metas (Seu planejamento)

            💡 _Dica: Você também pode me perguntar diretamente, tipo "como uso a aba de renda?" que eu já te explico._

            Digite *sair* a qualquer momento para fechar este guia.""";
    }

    public String getCapitulo(int numero) {
        return switch (numero) {
            case 1 -> """
                *1️⃣ ABA CONTAS — Onde seu dinheiro mora*

                Essa tela mostra o dinheiro real que você tem disponível agora. Ela une o saldo de todos os seus bancos — Itaú, Nubank, Inter, seja qual for — e te mostra o total no bolso de verdade.

                *O que fazer aqui:*
                • Cadastre suas contas com o saldo atual correto para o sistema ter uma base real.
                • Se você usa cheque especial, registre o limite para o J.A.R.V.I.S. saber até onde você pode ir sem perigo.

                *Pelo WhatsApp você pode:*
                • Perguntar: _"quanto tenho nas contas?"_
                • Transferir entre contas: _"passa 50 do Itaú pro Nubank"_
                • Consultar um banco específico: _"saldo do Nubank"_

                _Digite outro número para continuar ou *sair* para fechar o guia._""";
            case 2 -> """
                *2️⃣ ABA TRANSAÇÕES — O diário das suas finanças*

                Tudo o que entra e sai de dinheiro fica registrado aqui. É o histórico completo da sua vida financeira — gastos, recebimentos e transferências.

                A boa notícia: você não precisa abrir o app toda vez. Pode registrar tudo por aqui, no WhatsApp, falando normalmente.

                *Pelo WhatsApp você pode:*
                • Lançar um gasto: _"gastei 45 no Uber pelo Nubank"_
                • Registrar uma entrada: _"recebi um PIX de 150"_
                • Lançar uma compra no crédito: _"comprei 200 de roupa no cartão Itaú"_

                📎 *Truque do J.A.R.V.I.S.:* Tirou foto de um comprovante de PIX ou de um cupom fiscal? Manda a imagem aqui no chat. Eu leio os valores sozinho e já lanço o gasto para você, sem digitar nada.

                _Digite outro número para continuar ou *sair* para fechar o guia._""";
            case 3 -> """
                *3️⃣ ABA CARTÕES E FATURAS — Cuidado com o vilão!*

                Aqui ficam os seus cartões de crédito. O sistema separa o que você já gastou no cartão do que ainda está disponível de limite — para você nunca ser pego de surpresa na hora de pagar.

                *O que você vê nessa tela:*
                • O limite total do seu cartão e quanto ainda está livre.
                • Os gastos do mês atual e a previsão de fechamento da fatura.

                *Pelo WhatsApp você pode:*
                • Perguntar: _"status do cartão Nubank"_ ou _"qual meu limite disponível?"_
                • Consultar os gastos: _"quanto gastei no Itaú esse mês?"_

                📄 *Truque do J.A.R.V.I.S.:* Quando sua fatura fechar, arraste o PDF dela direto para este chat. Pode ser do Itaú, Nubank, Bradesco, Inter — eu leio a fatura inteira e lanço todos os gastos de uma vez, sem você precisar digitar nada.

                _Digite outro número para continuar ou *sair* para fechar o guia._""";
            case 4 -> """
                *4️⃣ ABA RENDA — Seu motor financeiro*

                Essa tela serve para o sistema entender como você ganha dinheiro. Com essa informação, o J.A.R.V.I.S. consegue calcular se você está gastando dentro do que seu salário ou faturamento permite.

                O sistema funciona para 3 tipos de perfil:

                *Contracheque (CLT):* Para quem recebe um salário fixo num dia certo do mês. Você informa o valor e o dia de pagamento, e o sistema trabalha em cima disso.

                *Recebimento Único:* Para quem recebe um valor fixo sem ser CLT — como aluguel, pró-labore ou pensão. Sem holerite, mas com previsibilidade.

                *Fluxo Diário:* Para autônomos, motoristas, profissionais liberais ou quem recebe vários PIX de valores variados ao longo do mês. O sistema calcula a sua média de ganhos dos últimos 90 dias e cria uma meta de faturamento para te guiar.

                _Configure seu perfil acessando a aba Renda no app._

                _Digite outro número para continuar ou *sair* para fechar o guia._""";
            case 5 -> """
                *5️⃣ ABA ORÇAMENTOS E METAS — Seu futuro protegido*

                Aqui é onde você define as regras do jogo para o seu dinheiro.

                *Orçamentos* são os tetos de gasto por categoria. Você diz: "no máximo R$ 800 em Mercado esse mês" — e o sistema monitora se você está respeitando esse limite ou não.

                *Metas* são os seus sonhos com data e valor. Você diz: "quero guardar R$ 3.000 para a viagem de férias" — e o sistema te mostra o quanto você já separou e quanto falta.

                *Os sinalizadores de saúde financeira:*
                🟢 *Verde:* Dentro do planejado, sem preocupação.
                🟡 *Amarelo:* Você já usou mais de 80% do limite da categoria. Hora de prestar atenção.
                🔴 *Vermelho:* O orçamento foi estourado. O J.A.R.V.I.S. vai te avisar.

                *Pelo WhatsApp você pode:*
                • Perguntar: _"quanto gastei do meu orçamento?"_
                • Ver o resumo: _"como estão minhas metas?"_ ou _"resumo do mês"_

                _Configure acessando a aba Orçamentos e Metas no app._

                _Digite outro número para continuar ou *sair* para fechar o guia._""";
            default -> getMenuInicial();
        };
    }

    public String getMensagemEncerramento() {
        return """
            ✅ *Guia encerrado, chefe.*

            Voltei para o modo de operação padrão. Se precisar do guia novamente, é só digitar *tutorial* ou *como usar*.

            O que precisa agora?""";
    }

    private static void iniciar(TutorialSessao sessao) {
        sessao.setEmTutorial(true);
        sessao.setCapituloAtual(0);
    }

    private static void encerrar(TutorialSessao sessao) {
        sessao.setEmTutorial(false);
        sessao.setCapituloAtual(0);
    }

    private static boolean isIniciarTutorial(String norm) {
        if (norm.equals("tutorial")) {
            return true;
        }
        if (norm.contains("guia de uso") || norm.contains("guia de operacoes")) {
            return true;
        }
        if (norm.equals("como usar") || norm.startsWith("como usar ")) {
            return true;
        }
        if (norm.contains("ajuda tutorial") || norm.contains("tutorial ajuda")) {
            return true;
        }
        if (norm.contains("me ensina a usar o sistema") || norm.contains("me ensina a usar")) {
            return true;
        }
        if (norm.contains("me ensina") && norm.contains("sistema")) {
            return true;
        }
        if (norm.contains("como funciona isso aqui")) {
            return true;
        }
        if (norm.equals("como funciona") || norm.startsWith("como funciona ")) {
            return true;
        }
        if (norm.equals("help") || norm.equals("ajuda")) {
            return true;
        }
        if (norm.contains("nao sei usar") || norm.contains("nao sei como usar")) {
            return true;
        }
        if (norm.contains("me explica o sistema")) {
            return true;
        }
        return parecePerguntaSobreAba(norm);
    }

    /** Detecta pergunta natural sobre uma aba/tela (ex.: "como uso a aba de cartoes?"). */
    private static boolean parecePerguntaSobreAba(String norm) {
        boolean temIndicadorAba = norm.contains("aba")
            || norm.contains("tela")
            || norm.contains("menu")
            || norm.contains("pagina")
            || norm.contains("secao");
        boolean temPergunta = norm.contains("como uso")
            || norm.contains("como funciona")
            || norm.contains("me explica")
            || norm.contains("nao entendo")
            || norm.contains("o que e")
            || norm.contains("para que serve");
        return temIndicadorAba && temPergunta;
    }

    /**
     * Identifica capítulo 1–5 a partir de linguagem natural sobre abas.
     * Retorna 0 se não houver aba específica ou se parecer comando financeiro operacional.
     */
    private static int extrairCapituloDaMensagem(String norm) {
        if (norm.isBlank()) {
            return 0;
        }
        boolean contextoGuia = parecePerguntaSobreAba(norm)
            || norm.contains("tutorial")
            || norm.contains("guia de uso")
            || norm.contains("guia de operacoes")
            || norm.contains("me ensina")
            || norm.contains("me explica")
            || norm.contains("como uso")
            || norm.contains("nao entendo")
            || norm.contains("nao sei usar")
            || norm.contains("o que e");

        if (!contextoGuia) {
            return 0;
        }

        if (mencionaCapitulo(norm, 5)) {
            return 5;
        }
        if (mencionaCapitulo(norm, 4)) {
            return 4;
        }
        if (mencionaCapitulo(norm, 3)) {
            return 3;
        }
        if (mencionaCapitulo(norm, 2)) {
            return 2;
        }
        if (mencionaCapitulo(norm, 1)) {
            return 1;
        }
        return 0;
    }

    private static boolean mencionaCapitulo(String norm, int capitulo) {
        return switch (capitulo) {
            case 1 -> norm.contains("conta bancaria") || norm.contains("contas bancarias")
                || (norm.contains("conta") || norm.contains("contas"))
                && !norm.contains("cartao") && !norm.contains("transacao");
            case 2 -> norm.contains("transacao") || norm.contains("transacoes")
                || norm.contains("lancamento") || norm.contains("lancamentos")
                || norm.contains("extrato") || norm.contains("historico de gastos")
                || norm.contains("entradas e saidas") || norm.contains("diario");
            case 3 -> norm.contains("cartao") || norm.contains("cartoes")
                || norm.contains("fatura") || norm.contains("faturas")
                || norm.contains("limite do cartao") || norm.contains("credito");
            case 4 -> norm.contains("renda") || norm.contains("salario") || norm.contains("contracheque")
                || norm.contains("holerite") || norm.contains("perfil financeiro")
                || norm.contains("fluxo diario") || norm.contains("recebimento unico")
                || norm.contains("clt") && (norm.contains("aba") || norm.contains("tela") || norm.contains("perfil"));
            case 5 -> norm.contains("orcamento") || norm.contains("orcamentos")
                || norm.contains("meta") || norm.contains("metas")
                || norm.contains("planejamento") || norm.contains("objetivo financeiro");
            default -> false;
        };
    }

    private static boolean isPararTutorial(String norm) {
        return norm.equals("sair")
            || norm.equals("desligar")
            || norm.equals("parar")
            || norm.equals("cancelar")
            || norm.equals("chega")
            || norm.equals("voltar ao normal")
            || norm.contains("encerrar tutorial")
            || norm.equals("encerrar");
    }

    private static boolean isPassoCapitulo(String norm) {
        return CAPITULO_NUMERO.matcher(norm).matches();
    }

    private static int extrairCapituloDeTexto(String sourceText) {
        if (sourceText == null) {
            return 0;
        }
        String norm = normalizar(sourceText.trim());
        if (CAPITULO_NUMERO.matcher(norm).matches()) {
            return Integer.parseInt(norm);
        }
        return 0;
    }

    private static String normalizar(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .trim();
    }

    @Getter
    @Setter
    static class TutorialSessao {
        private boolean emTutorial;
        private int capituloAtual;
    }
}
