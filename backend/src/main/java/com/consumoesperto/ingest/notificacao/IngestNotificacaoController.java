package com.consumoesperto.ingest.notificacao;

import com.consumoesperto.config.IngestNotificacaoProperties;
import com.consumoesperto.ingest.notificacao.dto.IngestNotificacaoRequest;
import com.consumoesperto.ingest.notificacao.security.IngestTokenAuthentication;
import com.consumoesperto.model.NotificacaoBancariaRecebida;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ingest/notificacao")
@RequiredArgsConstructor
public class IngestNotificacaoController {

    private final IngestNotificacaoAcceptService acceptService;
    private final IngestNotificacaoProcessor processor;
    private final IngestNotificacaoProperties properties;

    @PostMapping
    public ResponseEntity<Map<String, Object>> receber(
        Authentication authentication,
        @RequestBody IngestNotificacaoRequest body
    ) {
        if (!(authentication instanceof IngestTokenAuthentication ingest)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        NotificacaoBancariaRecebida row = acceptService.aceitar(ingest.getUsuarioId(), body);
        if (NotificacaoBancariaRecebida.STATUS_RECEBIDA.equals(row.getStatus())) {
            if (properties.isAsync()) {
                processor.processarAsync(row.getId());
            } else {
                processor.processar(row.getId());
            }
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
            "id", row.getId(),
            "status", "RECEBIDA"
        ));
    }
}
