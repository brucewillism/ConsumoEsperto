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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato dos relatórios PDF:
 * — endpoints legados que devolviam texto rotulado como PDF agora retornam 410 Gone;
 * — o endpoint real devolve PDF binário verdadeiro (magic bytes %PDF).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RelatorioPdfContratoHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private String token;

    @BeforeEach
    void seed() {
        String sfx = String.valueOf(System.nanoTime());
        Usuario u = new Usuario();
        u.setUsername("pdf_" + sfx);
        u.setEmail("pdf_" + sfx + "@test.local");
        u.setPassword(passwordEncoder.encode("SenhaTeste123!"));
        u.setNome("Pdf Teste");
        u = usuarioRepository.save(u);
        UserPrincipal principal = UserPrincipal.create(u);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        token = jwtTokenProvider.generateToken(auth);
    }

    @Test
    void legadoFinanceiroCompleto_retorna410ComSubstituto() throws Exception {
        mockMvc.perform(get("/api/relatorios/financeiro-completo")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.substituto").exists());
    }

    @Test
    void legadoTransacoes_retorna410() throws Exception {
        mockMvc.perform(get("/api/relatorios/transacoes")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isGone());
    }

    @Test
    void legadoFaturas_retorna410() throws Exception {
        mockMvc.perform(get("/api/relatorios/faturas")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isGone());
    }

    @Test
    void legado_naoDevolveTextoFingindoSerPdf() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/relatorios/financeiro-completo")
                .header("Authorization", "Bearer " + token))
            .andReturn();
        String contentType = res.getResponse().getContentType();
        assertTrue(contentType == null || !contentType.contains("application/pdf"),
            "Endpoint legado não pode se anunciar como application/pdf");
    }

    @Test
    void pdfReal_comecaComMagicBytesPdf() throws Exception {
        LocalDate hoje = LocalDate.now();
        // O relatório mensal exige dados no período (sem lançamentos nem renda → 404)
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/transacoes")
                .header("Authorization", "Bearer " + token)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{"
                    + "\"descricao\":\"Despesa para o PDF\","
                    + "\"valor\":42.00,"
                    + "\"tipoTransacao\":\"DESPESA\","
                    + "\"dataTransacao\":\"" + hoje.atStartOfDay() + "\","
                    + "\"statusConferencia\":\"CONFIRMADA\""
                    + "}"))
            .andExpect(status().isOk());

        MvcResult res = mockMvc.perform(get("/api/relatorios/mensal.pdf")
                .param("ano", String.valueOf(hoje.getYear()))
                .param("mes", String.valueOf(hoje.getMonthValue()))
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
        byte[] body = res.getResponse().getContentAsByteArray();
        assertTrue(body.length >= 4, "PDF não pode estar vazio");
        String magic = new String(body, 0, 4, StandardCharsets.US_ASCII);
        assertTrue(magic.equals("%PDF"), "Arquivo deve iniciar com %PDF, obteve: " + magic);
        String contentType = res.getResponse().getContentType();
        assertTrue(contentType != null && contentType.contains("application/pdf"));
    }

    @Test
    void disponiveis_anunciaSomenteEndpointsReais() throws Exception {
        mockMvc.perform(get("/api/relatorios/disponiveis")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.relatorios[0].endpoint").value("/api/relatorios/mensal.pdf"));
    }
}
