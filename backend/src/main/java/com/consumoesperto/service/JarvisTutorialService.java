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

        if (isIniciarTutorial(norm)) {
            iniciar(sessao);
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
            case "START_TUTORIAL" -> {
                iniciar(sessoes.computeIfAbsent(userId, id -> new TutorialSessao()));
                yield getMenuInicial();
            }
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

    public void encerrarUsuario(Long userId) {
        if (userId == null) {
            return;
        }
        sessoes.remove(userId);
    }

    public String getMenuInicial() {
        return """
            📖 *GUIA DE OPERAÇÕES — J.A.R.V.I.S.*

            Chefe, vou te mostrar como extrair o máximo do sistema. Digite o número do módulo:

            *1️⃣* — Contas e Patrimônio
            *2️⃣* — Registrar Lançamentos
            *3️⃣* — Cartões e Faturas
            *4️⃣* — Configurar sua Renda
            *5️⃣* — Orçamentos e Metas

            💡 Dica: comandos financeiros normais continuam funcionando aqui.
            Digite *sair* a qualquer momento para encerrar o guia.""";
    }

    public String getCapitulo(int numero) {
        return switch (numero) {
            case 1 -> """
                *1️⃣ CONTAS E PATRIMÔNIO*

                Chefe, aqui você tem controle total sobre seu saldo consolidado.

                Exemplos que funcionam agora:
                • "quanto tenho nas contas?"
                • "saldo do Nubank"
                • "transfere 100 do Itaú pro Nubank"
                • "qual minha reserva de emergência?"

                O Sentinela monitora o fluxo em tempo real e avisa quando o saldo real diverge da projeção.

                _Digite outro número para continuar ou *sair* para encerrar._""";
            case 2 -> """
                *2️⃣ REGISTRAR LANÇAMENTOS*

                Pode lançar no texto livre, chefe. O sistema entende linguagem natural.

                Exemplos:
                • "gastei 45 no uber pelo nubank"
                • "recebi 3200 de salário"
                • "paguei 180 de academia no cartão Itaú"

                📎 *Dica avançada:* Mande foto de comprovante ou PDF de extrato — processo automaticamente.

                O J.A.R.V.I.S. categoriza, debita da conta certa e atualiza o orçamento na hora.

                _Digite outro número para continuar ou *sair* para encerrar._""";
            case 3 -> """
                *3️⃣ CARTÕES E FATURAS*

                Consultas rápidas:
                • "status do cartão Nubank"
                • "quanto gastei no Itaú esse mês?"
                • "qual meu limite disponível?"

                📄 *Fatura PDF:* Mande o arquivo direto aqui. Processo o histórico inteiro e projeto o fechamento automaticamente.

                Trabalho com Itaú, Nubank, Bradesco, Inter e outros. Se a fatura vier em PDF, processo sem precisar de login.

                _Digite outro número para continuar ou *sair* para encerrar._""";
            case 4 -> """
                *4️⃣ CONFIGURAR SUA RENDA*

                Chefe, a precisão das projeções depende de como sua renda entra. Existem 3 modos:

                *CLT* — Salário fixo num dia certo do mês
                *Autônomo* — Recebimento único ou irregular
                *Fluxo Diário* — Múltiplos PIX ao longo do mês

                Para configurar: "minha renda é CLT, recebo dia 5"
                Ou: "recebo em média 4 mil por mês de forma variável"

                O Sentinela ajusta as projeções automaticamente depois da configuração.

                _Digite outro número para continuar ou *sair* para encerrar._""";
            case 5 -> """
                *5️⃣ ORÇAMENTOS E METAS*

                Consulte a saúde do mês assim:
                • "quanto gastei do meu orçamento?"
                • "como estão minhas metas?"
                • "resumo do mês"

                O sistema usa sinalizadores visuais:
                🟢 Dentro do orçamento
                🟡 Atenção — acima de 80%
                🔴 Limite estourado

                Para criar metas: "meta: economizar 500 reais em Lazer esse mês"

                _Digite outro número para continuar ou *sair* para encerrar._""";
            default -> getMenuInicial();
        };
    }

    public String getMensagemEncerramento() {
        return """
            ✅ *Tutorial encerrado, chefe.*

            Voltei para o modo de operação padrão. Se precisar do guia novamente, é só digitar *tutorial*.

            Como posso ajudá-lo agora?""";
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
        if (norm.contains("me ensina") || norm.contains("me explica o sistema")) {
            return true;
        }
        if (norm.equals("como funciona") || norm.startsWith("como funciona ")) {
            return true;
        }
        return norm.contains("me explica") && norm.contains("sistema");
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
