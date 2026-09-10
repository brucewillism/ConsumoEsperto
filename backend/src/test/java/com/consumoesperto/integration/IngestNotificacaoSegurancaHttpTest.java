package com.consumoesperto.integration;

import com.consumoesperto.ingest.notificacao.IngestTokenService;
import com.consumoesperto.ingest.notificacao.security.IngestTokenFilter;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "consumoesperto.ingest.notificacao.enabled=true",
    "consumoesperto.ingest.notificacao.require-https=false",
    "consumoesperto.ingest.notificacao.async=false",
    "consumoesperto.ingest.notificacao.rate-limit-per-minute=2"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IngestNotificacaoSegurancaHttpTest {

    private static final String BODY = """
        {"origem":"ANDROID_MACRODROID","app":"nubank","titulo":"Nubank",\
        "texto":"Compra aprovada: R$ 45,90 em PADARIA DO ZE"}
        """;

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private IngestTokenService tokenService;

    private Long usuarioId;
    private String tokenValido;

    @BeforeEach
    void seed() {
        String sfx = String.valueOf(System.nanoTime());
        Usuario u = new Usuario();
        u.setUsername("ing_" + sfx);
        u.setEmail("ing_" + sfx + "@test.local");
        u.setPassword(passwordEncoder.encode("SenhaTeste123!"));
        u.setNome("Ingest");
        usuarioId = usuarioRepository.save(u).getId();
        tokenValido = tokenService.gerar(usuarioId);
    }

    @Test
    void semToken_retorna401() throws Exception {
        mockMvc.perform(post("/api/ingest/notificacao")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenInvalido_retorna401() throws Exception {
        mockMvc.perform(post("/api/ingest/notificacao")
                .header(IngestTokenFilter.TOKEN_HEADER, "ce_ing_deadbeef")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenRevogado_retorna401() throws Exception {
        tokenService.revogar(usuarioId);
        mockMvc.perform(post("/api/ingest/notificacao")
                .header(IngestTokenFilter.TOKEN_HEADER, tokenValido)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void terceiraRequisicaoNaJanela_retorna429() throws Exception {
        mockMvc.perform(post("/api/ingest/notificacao")
                .header(IngestTokenFilter.TOKEN_HEADER, tokenValido)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/ingest/notificacao")
                .header(IngestTokenFilter.TOKEN_HEADER, tokenValido)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/ingest/notificacao")
                .header(IngestTokenFilter.TOKEN_HEADER, tokenValido)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isTooManyRequests());
    }
}
