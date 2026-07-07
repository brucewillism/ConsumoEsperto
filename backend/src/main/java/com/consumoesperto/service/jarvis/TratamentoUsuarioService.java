package com.consumoesperto.service.jarvis;

import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.util.AppTimeZone;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Único ponto do backend com literais de vocativo/saudação com gênero.
 * Demais formatadores devem delegar aqui.
 */
@Service
@RequiredArgsConstructor
public class TratamentoUsuarioService {

    public static final String VOCATIVO_PADRAO = "utilizador";
    public static final String TITULO_NEUTRO_FORMAL = "Senhor(a)";
    public static final String MARCADOR_COMPREENDIDO_SENHOR = "compreendido, senhor";
    public static final String MARCADOR_LAMENTO_SENHOR = "lamento, senhor";

    private static final List<String> SAUDACOES_MANHA = List.of(
        "Bom dia", "Um excelente dia", "Dia produtivo");
    private static final List<String> SAUDACOES_TARDE = List.of(
        "Boa tarde", "Boa tarde", "Espero que o dia esteja a correr bem");
    private static final List<String> SAUDACOES_NOITE = List.of(
        "Boa noite", "Boa noite", "Espero que tenha sido um bom dia");

    public String vocativoPadrao() {
        return VOCATIVO_PADRAO;
    }

    public String tituloNeutroFormal() {
        return TITULO_NEUTRO_FORMAL;
    }

    public String normalizarVocativo(String vocativo) {
        if (vocativo == null || vocativo.isBlank()) {
            return VOCATIVO_PADRAO;
        }
        return vocativo.trim();
    }

    public String vocativo(Usuario usuario) {
        if (usuario == null) {
            return VOCATIVO_PADRAO;
        }
        String v = usuario.getVocativo();
        if (v != null && !v.isBlank()) {
            return v.trim();
        }
        String t = usuario.getTratamento();
        if (t != null && !t.isBlank()) {
            return t.trim();
        }
        String pn = primeiroNome(usuario);
        return pn.isBlank() ? VOCATIVO_PADRAO : pn;
    }

    public String vocativoPorId(Long userId, UsuarioRepository repo) {
        if (userId == null || repo == null) {
            return VOCATIVO_PADRAO;
        }
        return repo.findById(userId).map(this::montarVocativoCompleto).orElse(VOCATIVO_PADRAO);
    }

    public String montarVocativoCompleto(Usuario usuario) {
        if (usuario == null) {
            return VOCATIVO_PADRAO;
        }
        if (usuario.getVocativo() != null && !usuario.getVocativo().isBlank()) {
            return usuario.getVocativo().trim();
        }
        if (Boolean.TRUE.equals(usuario.getJarvisConfigurado())) {
            String pn = primeiroNome(usuario);
            String t = usuario.getTratamento();
            if (t == null || t.isBlank()) {
                return pn.isBlank() ? VOCATIVO_PADRAO : pn;
            }
            String titulo = t.trim();
            return pn.isBlank() ? titulo : titulo + " " + pn;
        }
        String pn = primeiroNome(usuario);
        Usuario.PreferenciaTratamentoJarvis p = preferenciaJarvis(usuario);
        return switch (p) {
            case NENHUM -> pn.isBlank() ? VOCATIVO_PADRAO : pn;
            case SENHOR -> pn.isBlank() ? "Senhor" : "Senhor " + pn;
            case SENHORA -> pn.isBlank() ? "Senhora" : "Senhora " + pn;
            case DOUTOR -> pn.isBlank() ? "Doutor" : "Doutor " + pn;
            case DOUTORA -> pn.isBlank() ? "Doutora" : "Doutora " + pn;
            case AUTOMATICO -> vocativoPorGeneroInferido(usuario, pn);
        };
    }

    public String tituloEstrategico(Usuario usuario) {
        if (usuario == null) {
            return TITULO_NEUTRO_FORMAL;
        }
        if (Boolean.TRUE.equals(usuario.getJarvisConfigurado())) {
            String t = usuario.getTratamento();
            return (t == null || t.isBlank()) ? "" : t.trim();
        }
        Usuario.PreferenciaTratamentoJarvis p = preferenciaJarvis(usuario);
        return switch (p) {
            case SENHOR -> "Senhor";
            case SENHORA -> "Senhora";
            case DOUTOR -> "Doutor";
            case DOUTORA -> "Doutora";
            case NENHUM -> "";
            case AUTOMATICO -> switch (usuario.getGenero() != null ? usuario.getGenero() : Usuario.GeneroUsuario.UNKNOWN) {
                case MALE -> "Senhor";
                case FEMALE -> "Senhora";
                default -> TITULO_NEUTRO_FORMAL;
            };
        };
    }

    public String tratamentoConversacional(Usuario usuario, String forceVocative) {
        if (forceVocative != null && !forceVocative.isBlank()) {
            return forceVocative.trim();
        }
        if (usuario == null) {
            return "você";
        }
        if (usuario.getVocativo() != null && !usuario.getVocativo().isBlank()) {
            return usuario.getVocativo().trim();
        }
        if (Boolean.TRUE.equals(usuario.getJarvisConfigurado())) {
            String t = usuario.getTratamento();
            if (t != null && !t.isBlank()) {
                return t.trim();
            }
            String pn = primeiroNome(usuario);
            return pn.isBlank() ? "você" : pn;
        }
        Usuario.PreferenciaTratamentoJarvis p = preferenciaJarvis(usuario);
        String pn = primeiroNome(usuario);
        return switch (p) {
            case NENHUM -> pn.isBlank() ? "você" : pn;
            case SENHOR -> "senhor";
            case SENHORA -> "senhora";
            case DOUTOR -> "doutor";
            case DOUTORA -> "doutora";
            case AUTOMATICO -> switch (usuario.getGenero() != null ? usuario.getGenero() : Usuario.GeneroUsuario.UNKNOWN) {
                case MALE -> "senhor";
                case FEMALE -> "senhora";
                default -> pn.isBlank() ? "você" : pn;
            };
        };
    }

    public String prefixoVocativo(String vocativo) {
        return normalizarVocativo(vocativo) + ", ";
    }

    public String prefixoVocativo(Usuario usuario) {
        return vocativo(usuario) + ", ";
    }

    public String confirmando(String vocativo) {
        return "Confirmando, " + normalizarVocativo(vocativo) + ": ";
    }

    public String entendidoExclamacao(String vocativo) {
        return "Entendido, " + normalizarVocativo(vocativo) + "! ";
    }

    public String feitoExclamacao(String vocativo) {
        return "Feito, " + normalizarVocativo(vocativo) + "! ";
    }

    public String palavraFinal(String vocativo) {
        return "A palavra final é sua, " + normalizarVocativo(vocativo) + ".";
    }

    public String saudacaoPeriodo(Usuario usuario) {
        LocalTime now = AppTimeZone.agora().toLocalTime();
        String base;
        if (now.isBefore(LocalTime.of(12, 0))) {
            base = pick(SAUDACOES_MANHA);
        } else if (now.isBefore(LocalTime.of(18, 0))) {
            base = pick(SAUDACOES_TARDE);
        } else {
            base = pick(SAUDACOES_NOITE);
        }
        return base + ", " + vocativo(usuario) + "!";
    }

    public String bemVindoDeVolta(Usuario usuario) {
        return "Que bom te ver de volta, " + vocativo(usuario) + "!";
    }

    public String flexionar(String masculino, String feminino, String neutro, Usuario usuario) {
        return switch (generoGramatical(usuario)) {
            case FEMININO -> feminino;
            case MASCULINO -> masculino;
            case NEUTRO -> neutro != null ? neutro : masculino + "(a)";
        };
    }

    public Usuario.GeneroGramatical generoGramatical(Usuario usuario) {
        if (usuario == null) {
            return Usuario.GeneroGramatical.NEUTRO;
        }
        if (usuario.getGeneroGramatical() != null) {
            return usuario.getGeneroGramatical();
        }
        Usuario.GeneroUsuario g = usuario.getGenero();
        if (g == Usuario.GeneroUsuario.MALE) {
            return Usuario.GeneroGramatical.MASCULINO;
        }
        if (g == Usuario.GeneroUsuario.FEMALE) {
            return Usuario.GeneroGramatical.FEMININO;
        }
        return Usuario.GeneroGramatical.NEUTRO;
    }

    public boolean precisaColetarTratamento(Usuario usuario) {
        if (usuario == null) {
            return true;
        }
        if (Boolean.TRUE.equals(usuario.getTratamentoConfigurado())) {
            return false;
        }
        return !Boolean.TRUE.equals(usuario.getJarvisConfigurado());
    }

    public String instrucaoInterlocutorJarvis(Usuario usuario) {
        if (usuario == null) {
            return "";
        }
        if (Boolean.TRUE.equals(usuario.getJarvisConfigurado())) {
            String pn = primeiroNome(usuario);
            if (pn.isBlank()) {
                pn = VOCATIVO_PADRAO;
            }
            String t = usuario.getTratamento();
            if (t == null || t.isBlank()) {
                return "Você está falando com " + pn + ". " + INSTRUCAO_SEM_TITULO_FORMAL + "\n\n";
            }
            return "Você está falando com " + t.trim() + " " + pn + ". Utilize o vocativo \"" + t.trim()
                + "\" em momentos chave da conversa.\n\n";
        }
        Usuario.PreferenciaTratamentoJarvis p = preferenciaJarvis(usuario);
        String pn = primeiroNome(usuario);
        if (pn.isBlank()) {
            pn = VOCATIVO_PADRAO;
        }
        if (p == Usuario.PreferenciaTratamentoJarvis.NENHUM) {
            return "Você está falando com " + pn + ". " + INSTRUCAO_SEM_TITULO_FORMAL + "\n\n";
        }
        String title = tituloEstrategico(usuario);
        if (title.isBlank()) {
            return "Você está falando com " + pn + ". Trate o interlocutor pelo primeiro nome de forma respeitosa.\n\n";
        }
        return "Você está falando com " + title + " " + pn + ". Utilize o vocativo \"" + title + "\" em momentos chave da conversa.\n\n";
    }

    private static final String INSTRUCAO_SEM_TITULO_FORMAL =
        "Não utilize título formal (Senhor/Senhora/Doutor(a)); use o primeiro nome de forma respeitosa nos momentos chave da conversa";

    public String rotuloPreferenciaTratamento(Usuario.PreferenciaTratamentoJarvis pref) {
        if (pref == null) {
            return "";
        }
        return switch (pref) {
            case SENHOR -> "Senhor";
            case SENHORA -> "Senhora";
            case DOUTOR -> "Doutor";
            case DOUTORA -> "Doutora";
            case NENHUM, AUTOMATICO -> "";
        };
    }

    public static String primeiroNome(Usuario usuario) {
        if (usuario == null || usuario.getNome() == null || usuario.getNome().isBlank()) {
            return "";
        }
        return usuario.getNome().trim().split("\\s+")[0];
    }

    private static String vocativoPorGeneroInferido(Usuario usuario, String pn) {
        Usuario.GeneroUsuario g = usuario.getGenero() != null ? usuario.getGenero() : Usuario.GeneroUsuario.UNKNOWN;
        return switch (g) {
            case MALE -> pn.isBlank() ? "Senhor" : "Senhor " + pn;
            case FEMALE -> pn.isBlank() ? "Senhora" : "Senhora " + pn;
            default -> pn.isBlank() ? TITULO_NEUTRO_FORMAL : TITULO_NEUTRO_FORMAL + " " + pn;
        };
    }

    private static Usuario.PreferenciaTratamentoJarvis preferenciaJarvis(Usuario usuario) {
        return usuario.getPreferenciaTratamentoJarvis() != null
            ? usuario.getPreferenciaTratamentoJarvis()
            : Usuario.PreferenciaTratamentoJarvis.AUTOMATICO;
    }

    private static String pick(List<String> options) {
        return options.get(ThreadLocalRandom.current().nextInt(options.size()));
    }
}
