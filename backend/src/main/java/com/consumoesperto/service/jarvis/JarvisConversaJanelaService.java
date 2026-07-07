package com.consumoesperto.service.jarvis;

import com.consumoesperto.config.JarvisPerformanceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Janela deslizante de conversa multi-turno (últimas N trocas) para enriquecer o prompt de parse.
 */
@Service
@RequiredArgsConstructor
public class JarvisConversaJanelaService {

    private final JarvisPerformanceProperties props;

    private final Map<Long, ConversaState> porUsuario = new ConcurrentHashMap<>();

    public void registrarUsuario(Long userId, String texto) {
        if (userId == null || texto == null || texto.isBlank()) {
            return;
        }
        porUsuario.compute(userId, (id, state) -> {
            ConversaState s = state != null ? state : new ConversaState();
            s.expiraEm = Instant.now().plusSeconds(props.getConversaJanelaTtlSeconds());
            s.trocas.add(new Troca("user", texto.trim()));
            while (s.trocas.size() > props.getConversaJanelaMaxTrocas() * 2) {
                s.trocas.remove(0);
            }
            return s;
        });
    }

    public void registrarAssistente(Long userId, String resposta) {
        if (userId == null || resposta == null || resposta.isBlank()) {
            return;
        }
        porUsuario.compute(userId, (id, state) -> {
            if (state == null || state.expirou()) {
                return state;
            }
            state.trocas.add(new Troca("assistant", resposta.trim()));
            while (state.trocas.size() > props.getConversaJanelaMaxTrocas() * 2) {
                state.trocas.remove(0);
            }
            return state;
        });
    }

    public String montarBlocoHistorico(Long userId) {
        ConversaState s = porUsuario.get(userId);
        if (s == null || s.expirou() || s.trocas.isEmpty()) {
            if (s != null && s.expirou()) {
                porUsuario.remove(userId);
            }
            return "";
        }
        List<Troca> recentes = s.trocas.size() > props.getConversaJanelaMaxTrocas()
            ? s.trocas.subList(s.trocas.size() - props.getConversaJanelaMaxTrocas(), s.trocas.size())
            : s.trocas;
        StringBuilder sb = new StringBuilder("Histórico recente da conversa:\n");
        for (Troca t : recentes) {
            sb.append(t.role.equals("user") ? "Usuário: " : "J.A.R.V.I.S.: ")
                .append(t.texto.length() > 280 ? t.texto.substring(0, 277) + "..." : t.texto)
                .append('\n');
        }
        return sb.toString();
    }

    private static final class ConversaState {
        Instant expiraEm;
        final List<Troca> trocas = Collections.synchronizedList(new ArrayList<>());

        boolean expirou() {
            return expiraEm != null && Instant.now().isAfter(expiraEm);
        }
    }

    private record Troca(String role, String texto) {}
}
