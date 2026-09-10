package com.consumoesperto.ingest.notificacao.security;

import com.consumoesperto.model.IngestToken;
import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

@Getter
public class IngestTokenAuthentication extends AbstractAuthenticationToken {

    private final IngestToken tokenEntity;
    private final Long usuarioId;

    public IngestTokenAuthentication(IngestToken tokenEntity) {
        super(List.of(new SimpleGrantedAuthority("ROLE_INGEST")));
        this.tokenEntity = tokenEntity;
        this.usuarioId = tokenEntity.getUsuario().getId();
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return tokenEntity;
    }
}
