package com.consumoesperto.service;

import com.consumoesperto.model.UsuarioSessaoContexto;
import com.consumoesperto.repository.UsuarioSessaoContextoRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Sessões efêmeras de contexto conversacional (WhatsApp NLU stateful).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UsuarioSessaoContextoService {

    public static final String CANAL_WHATSAPP = "WHATSAPP";
    public static final String CHAVE_COMPROVANTE_CONFIRMACAO = "COMPROVANTE_CONFIRMACAO";
    /** Boleto/Pix lido aguardando confirmação "sim/não" para agendar pagamento. */
    public static final String CHAVE_AGENDAMENTO_PAGAMENTO = "AGENDAMENTO_PAGAMENTO_CONFIRMACAO";

    private final UsuarioSessaoContextoRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void salvar(Long usuarioId, String canal, String chaveSessao, Map<String, Object> contexto, int minutosTtl) {
        try {
            String json = objectMapper.writeValueAsString(contexto);
            UsuarioSessaoContexto sessao = repository
                .findByUsuarioIdAndCanalAndChaveSessao(usuarioId, canal, chaveSessao)
                .orElseGet(UsuarioSessaoContexto::new);
            sessao.setUsuarioId(usuarioId);
            sessao.setCanal(canal);
            sessao.setChaveSessao(chaveSessao);
            sessao.setContextoJson(json);
            sessao.setExpiraEm(LocalDateTime.now().plusMinutes(minutosTtl));
            repository.save(sessao);
        } catch (Exception e) {
            log.warn("Falha ao salvar sessão contexto userId={} chave={}: {}", usuarioId, chaveSessao, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> buscarAtiva(Long usuarioId, String canal, String chaveSessao) {
        return repository.findByUsuarioIdAndCanalAndChaveSessao(usuarioId, canal, chaveSessao)
            .filter(s -> s.getExpiraEm() == null || s.getExpiraEm().isAfter(LocalDateTime.now()))
            .map(this::parseContexto);
    }

    @Transactional
    public void remover(Long usuarioId, String canal, String chaveSessao) {
        repository.findByUsuarioIdAndCanalAndChaveSessao(usuarioId, canal, chaveSessao)
            .ifPresent(repository::delete);
    }

    @Transactional
    public int limparExpiradas() {
        return repository.deleteExpiradas(LocalDateTime.now());
    }

    private Map<String, Object> parseContexto(UsuarioSessaoContexto sessao) {
        try {
            return objectMapper.readValue(sessao.getContextoJson(), new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Contexto JSON inválido sessaoId={}: {}", sessao.getId(), e.getMessage());
            return Map.of();
        }
    }
}
