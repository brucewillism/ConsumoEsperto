package com.consumoesperto.ingest.notificacao;

import com.consumoesperto.config.IngestNotificacaoProperties;
import com.consumoesperto.ingest.notificacao.security.IngestTokenHasher;
import com.consumoesperto.model.IngestToken;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.IngestTokenRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.util.AppTimeZone;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IngestTokenService {

    private final IngestTokenRepository tokenRepository;
    private final UsuarioRepository usuarioRepository;
    private final IngestNotificacaoProperties properties;

    @Transactional(readOnly = true)
    public Optional<IngestToken> ativoDoUsuario(Long usuarioId) {
        return tokenRepository.findFirstByUsuarioIdAndRevogadoFalseOrderByCriadoEmDesc(usuarioId);
    }

    @Transactional(readOnly = true)
    public Optional<IngestToken> ultimoDoUsuario(Long usuarioId) {
        return tokenRepository.findFirstByUsuarioIdOrderByCriadoEmDesc(usuarioId);
    }

    @Transactional
    public String gerar(Long usuarioId) {
        ativoDoUsuario(usuarioId).ifPresent(t -> {
            t.setRevogado(true);
            tokenRepository.save(t);
        });
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));
        String raw = IngestTokenHasher.generateToken();
        IngestToken entity = new IngestToken();
        entity.setUsuario(usuario);
        entity.setTokenHash(IngestTokenHasher.hashToken(raw));
        entity.setPrefixo(IngestTokenHasher.prefixo(raw));
        entity.setCriadoEm(AppTimeZone.agora());
        entity.setRevogado(false);
        tokenRepository.save(entity);
        return raw;
    }

    @Transactional
    public void revogar(Long usuarioId) {
        ativoDoUsuario(usuarioId).ifPresent(t -> {
            t.setRevogado(true);
            tokenRepository.save(t);
        });
    }

    public String ingestionUrl() {
        String base = properties.getIngestionBaseUrl();
        if (base == null || base.isBlank()) {
            return "/api/ingest/notificacao";
        }
        return base.replaceAll("/$", "") + "/api/ingest/notificacao";
    }
}
