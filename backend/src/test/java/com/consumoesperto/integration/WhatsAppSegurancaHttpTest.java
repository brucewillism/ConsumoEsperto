package com.consumoesperto.integration;

import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.security.JwtTokenProvider;
import com.consumoesperto.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Segurança da configuração WhatsApp e do webhook Evolution:
 * - webhook exige segredo (fail-closed via EvolutionWebhookApiKeyFilter);
 * - webhook rejeita payload acima do limite (413);
 * - endpoints /api/whatsapp/** e /api/usuarios/whatsapp/** exigem autenticação;
 * - operação de manutenção global exige ROLE_ADMIN;
 * - usuário B não vê o número WhatsApp do usuário A.
 */
@SpringBootTest(properties = {
    "evolution.webhook.auth-required=true",
    "evolution.webhook.secret=segredo-webhook-teste",
    "consumoesperto.whatsapp.webhook.max-payload-bytes=2048"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WhatsAppSegurancaHttpTest {

    private static final String SECRET_HEADER = "X-ConsumoEsperto-Webhook-Secret";
    private static final String SECRET = "segredo-webhook-teste";

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private Usuario userA;
    private Usuario userB;
    private String tokenB;

    @BeforeEach
    void seed() {
        String sfx = String.valueOf(System.nanoTime());
        userA = saveUser("wa_a_" + sfx, "wa_a_" + sfx + "@test.local", "5511999990001");
        userB = saveUser("wa_b_" + sfx, "wa_b_" + sfx + "@test.local", null);
        tokenB = bearerFor(userB);
    }

    // ------------------------------------------------------------------
    // Webhook Evolution: segredo obrigatório (fail-closed)
    // ------------------------------------------------------------------

    @Test
    void webhookSemCredencial_retorna401() throws Exception {
        mockMvc.perform(post("/api/public/evolution/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"event\":\"messages.upsert\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void webhookComSegredoErrado_retorna401() throws Exception {
        mockMvc.perform(post("/api/public/evolution/webhook")
                .header(SECRET_HEADER, "segredo-errado")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"event\":\"messages.upsert\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void webhookComSegredoCorreto_aceita() throws Exception {
        mockMvc.perform(post("/api/public/evolution/webhook")
                .header(SECRET_HEADER, SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"event\":\"evento.desconhecido\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void webhookNaRotaAlternativa_tambemExigeSegredo() throws Exception {
        mockMvc.perform(post("/api/whatsapp/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"event\":\"messages.upsert\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void webhookComPayloadAcimaDoLimite_retorna413() throws Exception {
        // Limite configurado no teste: 2048 bytes
        String grande = "{\"event\":\"messages.upsert\",\"padding\":\""
            + "x".repeat(4096) + "\"}";
        mockMvc.perform(post("/api/public/evolution/webhook")
                .header(SECRET_HEADER, SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content(grande))
            .andExpect(status().isPayloadTooLarge());
    }

    // ------------------------------------------------------------------
    // /api/whatsapp/** deixa de ser público (exceto webhook)
    // ------------------------------------------------------------------

    @Test
    void paridadeSemToken_retorna401() throws Exception {
        mockMvc.perform(get("/api/whatsapp/paridade"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void paridadeComToken_retorna200() throws Exception {
        mockMvc.perform(get("/api/whatsapp/paridade")
                .header("Authorization", "Bearer " + tokenB))
            .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // Configuração WhatsApp por usuário exige autenticação
    // ------------------------------------------------------------------

    @Test
    void endpointsDeConfiguracaoWhatsapp_semToken_retornam401() throws Exception {
        mockMvc.perform(post("/api/usuarios/whatsapp/vincular")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":\"5511999990009\"}"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/usuarios/whatsapp/desvincular"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/usuarios/whatsapp/evolution-desligar"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/usuarios/whatsapp/evolution-connection-status"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void manutencaoGlobalDePrivacidade_usuarioComum_retorna403() throws Exception {
        mockMvc.perform(post("/api/usuarios/whatsapp/evolution-privacy-settings-all")
                .header("Authorization", "Bearer " + tokenB))
            .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Privacidade: B não vê número do A
    // ------------------------------------------------------------------

    @Test
    void perfilDeB_naoContemNumeroWhatsappDeA() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/usuarios/perfil")
                .header("Authorization", "Bearer " + tokenB))
            .andExpect(status().isOk())
            .andReturn();
        assertFalse(res.getResponse().getContentAsString().contains("5511999990001"),
            "Perfil do usuário B não pode conter o número WhatsApp do usuário A");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Usuario saveUser(String username, String email, String whatsapp) {
        Usuario u = new Usuario();
        u.setUsername(username);
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode("SenhaTeste123!"));
        u.setNome("Test " + username);
        u.setWhatsappNumero(whatsapp);
        return usuarioRepository.save(u);
    }

    private String bearerFor(Usuario usuario) {
        UserPrincipal principal = UserPrincipal.create(usuario);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return jwtTokenProvider.generateToken(auth);
    }
}
