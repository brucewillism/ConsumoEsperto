package com.consumoesperto.service.jarvis;

import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class JarvisTratamentoWhatsappService {

    private static final Pattern ME_CHAME = Pattern.compile(
        "(?i)(?:jarvis[,\\s]+)?(?:me\\s+chame|me\\s+chama|pode\\s+me\\s+chamar|chame\\s+me)\\s+(?:de\\s+)?[\"']?(.+?)[\"']?\\s*$");

    private static final Pattern ESCOLHA_TRATAMENTO = Pattern.compile(
        "(?i)^(chefe|chefa|senhor|senhora|doutor|doutora|nenhum|s[oó]\\s+meu\\s+nome|s[oó]\\s+o\\s+nome|outro)\\b");

    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;
    private final TratamentoUsuarioService tratamentoUsuarioService;

    public boolean precisaColetarTratamento(Usuario usuario) {
        return tratamentoUsuarioService.precisaColetarTratamento(usuario);
    }

    public Optional<String> tryColetarTratamento(Long userId, String text) {
        if (userId == null || text == null || text.isBlank()) {
            return Optional.empty();
        }
        Usuario u = usuarioRepository.findById(userId).orElse(null);
        if (u == null || !tratamentoUsuarioService.precisaColetarTratamento(u)) {
            return Optional.empty();
        }
        String norm = normalize(text);
        if (norm.contains("como prefere") || norm.contains("como prefiro")) {
            return Optional.empty();
        }
        Matcher esc = ESCOLHA_TRATAMENTO.matcher(text.trim());
        if (esc.find()) {
            return Optional.of(aplicarEscolha(userId, u, esc.group(1)));
        }
        if (text.trim().length() <= 32 && !text.contains("?")) {
            return Optional.of(aplicarVocativoLivre(userId, u, text.trim()));
        }
        return Optional.of(montarPerguntaInicial(u));
    }

    public Optional<String> tryAtualizarVocativo(Long userId, String text) {
        if (userId == null || text == null) {
            return Optional.empty();
        }
        Matcher m = ME_CHAME.matcher(text.trim());
        if (!m.find()) {
            return Optional.empty();
        }
        String voc = m.group(1).trim();
        if (voc.isBlank() || voc.length() > 48) {
            return Optional.empty();
        }
        Usuario u = usuarioRepository.findById(userId).orElse(null);
        if (u == null) {
            return Optional.empty();
        }
        return Optional.of(aplicarVocativoLivre(userId, u, voc));
    }

    public String montarPerguntaInicial(Usuario usuario) {
        String pn = TratamentoUsuarioService.primeiroNome(usuario);
        String nome = pn.isBlank() ? "" : " " + pn;
        return "Antes de continuarmos" + nome + ", como prefere que eu te chame?\n\n"
            + "Sugestões: *chefe*, *chefa*, *Senhor(a)* + nome, *só meu nome*, ou digite outro vocativo (ex.: *capitã*).";
    }

    @Transactional
    public String aplicarVocativoLivre(Long userId, Usuario u, String vocativo) {
        u.setVocativo(vocativo);
        u.setTratamento(vocativo);
        u.setTratamentoConfigurado(true);
        u.setJarvisConfigurado(true);
        u.setGeneroConfirmado(true);
        if ("chefa".equalsIgnoreCase(vocativo) || "senhora".equalsIgnoreCase(vocativo)
            || "doutora".equalsIgnoreCase(vocativo) || "capitã".equalsIgnoreCase(vocativo)
            || "capita".equalsIgnoreCase(vocativo)) {
            u.setGeneroGramatical(Usuario.GeneroGramatical.FEMININO);
        } else if ("chefe".equalsIgnoreCase(vocativo) || "senhor".equalsIgnoreCase(vocativo)
            || "doutor".equalsIgnoreCase(vocativo)) {
            u.setGeneroGramatical(Usuario.GeneroGramatical.MASCULINO);
        } else {
            u.setGeneroGramatical(Usuario.GeneroGramatical.NEUTRO);
        }
        u.setPreferenciaTratamentoJarvis(Usuario.PreferenciaTratamentoJarvis.NENHUM);
        usuarioRepository.save(u);
        return "Fechado, *" + vocativo + "*! Vou usar esse tratamento daqui em diante.";
    }

    private String aplicarEscolha(Long userId, Usuario u, String escolha) {
        String lc = normalize(escolha);
        if (lc.startsWith("chefa")) {
            return aplicarVocativoLivre(userId, u, "chefa");
        }
        if (lc.startsWith("chefe")) {
            return aplicarVocativoLivre(userId, u, "chefe");
        }
        if (lc.startsWith("senhora") || lc.startsWith("doutora")) {
            usuarioService.atualizarPerfilJarvis(userId, "SENHORA");
            u.setTratamentoConfigurado(true);
            u.setGeneroGramatical(Usuario.GeneroGramatical.FEMININO);
            usuarioRepository.save(u);
            return "Perfeito! Vou tratar-te como *Senhora* "
                + TratamentoUsuarioService.primeiroNome(u) + ".";
        }
        if (lc.startsWith("senhor") || lc.startsWith("doutor")) {
            usuarioService.atualizarPerfilJarvis(userId, "SENHOR");
            u.setTratamentoConfigurado(true);
            u.setGeneroGramatical(Usuario.GeneroGramatical.MASCULINO);
            usuarioRepository.save(u);
            return "Perfeito! Vou tratar-te como *Senhor* "
                + TratamentoUsuarioService.primeiroNome(u) + ".";
        }
        if (lc.contains("nome")) {
            String pn = TratamentoUsuarioService.primeiroNome(u);
            return aplicarVocativoLivre(userId, u, pn.isBlank() ? "você" : pn);
        }
        return montarPerguntaInicial(u);
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
    }
}
