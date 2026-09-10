package com.consumoesperto.ingest.notificacao;

import com.consumoesperto.model.IngestFonteRecurso;
import com.consumoesperto.model.IngestPreferencia;
import com.consumoesperto.model.IngestToken;
import com.consumoesperto.model.NotificacaoBancariaRecebida;
import com.consumoesperto.repository.NotificacaoBancariaRecebidaRepository;
import com.consumoesperto.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ingest/config")
@RequiredArgsConstructor
public class IngestNotificacaoConfigController {

    private final IngestTokenService tokenService;
    private final IngestPreferenciaService preferenciaService;
    private final IngestFonteRecursoService fonteRecursoService;
    private final NotificacaoBancariaRecebidaRepository recebidaRepository;

    @GetMapping
    public ResponseEntity<Map<String, Object>> obter(@AuthenticationPrincipal UserPrincipal user) {
        IngestPreferencia pref = preferenciaService.obterOuCriar(user.getId());
        Map<String, Object> token = new LinkedHashMap<>();
        tokenService.ultimoDoUsuario(user.getId()).ifPresentOrElse(t -> {
            token.put("existe", true);
            token.put("ativo", !t.isRevogado());
            token.put("prefixo", t.getPrefixo());
            token.put("criadoEm", t.getCriadoEm());
            token.put("ultimoUsoEm", t.getUltimoUsoEm());
        }, () -> token.put("existe", false));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ingestionUrl", tokenService.ingestionUrl());
        body.put("token", token);
        body.put("preferencias", toPrefMap(pref));
        body.put("fontes", fonteRecursoService.listar(user.getId()).stream().map(this::toFonteMap).toList());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/token")
    public ResponseEntity<Map<String, String>> gerarToken(@AuthenticationPrincipal UserPrincipal user) {
        String raw = tokenService.gerar(user.getId());
        return ResponseEntity.ok(Map.of(
            "token", raw,
            "ingestionUrl", tokenService.ingestionUrl(),
            "aviso", "Copie agora — o token não será mostrado de novo."
        ));
    }

    @PostMapping("/token/revogar")
    public ResponseEntity<Map<String, String>> revogarToken(@AuthenticationPrincipal UserPrincipal user) {
        tokenService.revogar(user.getId());
        return ResponseEntity.ok(Map.of("status", "REVOKED"));
    }

    @PutMapping("/preferencias")
    public ResponseEntity<Map<String, Object>> preferencias(
        @AuthenticationPrincipal UserPrincipal user,
        @RequestBody Map<String, Object> body
    ) {
        IngestPreferencia p = preferenciaService.atualizar(
            user.getId(),
            asBoolean(body.get("ativo")),
            asString(body.get("agrupamento")),
            asInt(body.get("resumoMinutos")),
            asTime(body.get("silenciosoInicio")),
            asTime(body.get("silenciosoFim")),
            asLong(body.get("contaPadraoId"))
        );
        return ResponseEntity.ok(toPrefMap(p));
    }

    @PostMapping("/fontes")
    public ResponseEntity<Map<String, Object>> upsertFonte(
        @AuthenticationPrincipal UserPrincipal user,
        @RequestBody Map<String, Object> body
    ) {
        IngestFonteRecurso row = fonteRecursoService.upsert(
            user.getId(),
            asString(body.get("app")),
            asString(body.get("canal")),
            asLong(body.get("contaBancariaId")),
            asLong(body.get("cartaoCreditoId"))
        );
        return ResponseEntity.ok(toFonteMap(row));
    }

    @DeleteMapping("/fontes/{id}")
    public ResponseEntity<Void> removerFonte(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable Long id
    ) {
        fonteRecursoService.remover(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/notificacoes")
    public ResponseEntity<List<Map<String, Object>>> log(
        @AuthenticationPrincipal UserPrincipal user,
        @RequestParam(defaultValue = "50") int limit
    ) {
        int size = Math.max(1, Math.min(100, limit));
        List<Map<String, Object>> items = recebidaRepository
            .findByUsuarioIdOrderByCriadoEmDesc(user.getId(), PageRequest.of(0, size))
            .stream()
            .map(this::toLogMap)
            .toList();
        return ResponseEntity.ok(items);
    }

    private Map<String, Object> toPrefMap(IngestPreferencia p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ativo", p.isAtivo());
        m.put("agrupamento", p.getAgrupamento());
        m.put("resumoMinutos", p.getResumoMinutos());
        m.put("silenciosoInicio", p.getSilenciosoInicio());
        m.put("silenciosoFim", p.getSilenciosoFim());
        m.put("contaPadraoId", p.getContaPadraoId());
        return m;
    }

    private Map<String, Object> toFonteMap(IngestFonteRecurso f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId());
        m.put("app", f.getApp());
        m.put("canal", f.getCanal());
        m.put("contaBancariaId", f.getContaBancariaId());
        m.put("cartaoCreditoId", f.getCartaoCreditoId());
        return m;
    }

    private Map<String, Object> toLogMap(NotificacaoBancariaRecebida n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.getId());
        m.put("origem", n.getOrigem());
        m.put("app", n.getApp());
        m.put("titulo", n.getTitulo());
        m.put("texto", n.getTexto());
        m.put("status", n.getStatus());
        m.put("erro", n.getErro());
        m.put("transacaoId", n.getTransacaoId());
        m.put("recebidoEm", n.getRecebidoEm());
        m.put("criadoEm", n.getCriadoEm());
        return m;
    }

    private static Boolean asBoolean(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(v.toString());
    }

    private static String asString(Object v) {
        return v == null ? null : v.toString();
    }

    private static Integer asInt(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(v.toString());
    }

    private static Long asLong(Object v) {
        if (v == null || "".equals(v.toString().trim())) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(v.toString());
    }

    private static LocalTime asTime(Object v) {
        if (v == null || v.toString().isBlank()) {
            return null;
        }
        return LocalTime.parse(v.toString().trim());
    }
}
