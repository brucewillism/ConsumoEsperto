package com.consumoesperto.integration;

import com.consumoesperto.dto.UsuarioDTO;
import com.consumoesperto.model.Categoria;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.CategoriaRepository;
import com.consumoesperto.repository.ContaBancariaRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.security.JwtTokenProvider;
import com.consumoesperto.security.UserPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ExportacaoPdfMotorHttpIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private ContaBancariaRepository contaBancariaRepository;
    @Autowired private TransacaoRepository transacaoRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private String tokenA;
    private String tokenB;
    private Long contaIdA;
    private Long categoriaIdA;
    private String nomeUsuarioA;

    @BeforeEach
    void seed() {
        String suffix = String.valueOf(System.nanoTime());
        Usuario a = saveUser("pdf_a_" + suffix, "pdf_a_" + suffix + "@test.local", "Relatório Ação Test");
        Usuario b = saveUser("pdf_b_" + suffix, "pdf_b_" + suffix + "@test.local", "Usuario B");
        nomeUsuarioA = a.getNome();

        Categoria cat = new Categoria();
        cat.setNome("Alimentação");
        cat.setUsuario(a);
        categoriaIdA = categoriaRepository.save(cat).getId();

        ContaBancaria conta = new ContaBancaria();
        conta.setNome("Conta PDF");
        conta.setUsuario(a);
        conta.setTipo(ContaBancaria.TipoConta.CORRENTE);
        conta.setSaldoAtual(new BigDecimal("2000.00"));
        conta.setAtiva(true);
        conta.setPadrao(true);
        contaIdA = contaBancariaRepository.save(conta).getId();

        Transacao despesa = new Transacao();
        despesa.setUsuario(a);
        despesa.setDescricao("Despesa única integração");
        despesa.setValor(new BigDecimal("75.50"));
        despesa.setTipoTransacao(Transacao.TipoTransacao.DESPESA);
        despesa.setCategoria(cat);
        despesa.setContaBancaria(conta);
        despesa.setDataTransacao(LocalDateTime.now());
        transacaoRepository.save(despesa);

        Transacao receita = new Transacao();
        receita.setUsuario(a);
        receita.setDescricao("Receita integração");
        receita.setValor(new BigDecimal("500.00"));
        receita.setTipoTransacao(Transacao.TipoTransacao.RECEITA);
        receita.setCategoria(cat);
        receita.setContaBancaria(conta);
        receita.setDataTransacao(LocalDateTime.now());
        transacaoRepository.save(receita);

        tokenA = bearerFor(a);
        tokenB = bearerFor(b);
    }

    @Test
    void csvExport_contemCabecalhoBomIsolamento() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/exportacao/csv/transacoes")
                .header("Authorization", "Bearer " + tokenA)
                .param("dataInicio", YearMonth.now().atDay(1).toString())
                .param("dataFim", YearMonth.now().atEndOfMonth().toString())
                .param("contaId", contaIdA.toString()))
            .andExpect(status().isOk())
            .andReturn();

        byte[] body = res.getResponse().getContentAsByteArray();
        assertTrue(body.length >= 3 && body[0] == (byte) 0xEF && body[1] == (byte) 0xBB && body[2] == (byte) 0xBF);
        String csv = new String(body, StandardCharsets.UTF_8);
        assertTrue(csv.contains("Data,Descrição,Tipo,Valor,Categoria"));
        assertTrue(csv.contains("Despesa única integração"));
        assertFalse(csv.contains("Usuario B"));
    }

    @Test
    void csvExport_rejeitaContaDeOutroUsuario() throws Exception {
        mockMvc.perform(get("/api/exportacao/csv/transacoes")
                .header("Authorization", "Bearer " + tokenA)
                .param("contaId", "999999"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void pdfMensal_contemDadosUsuario() throws Exception {
        YearMonth ym = YearMonth.now();
        MvcResult res = mockMvc.perform(get("/api/relatorios/mensal.pdf")
                .header("Authorization", "Bearer " + tokenA)
                .param("ano", String.valueOf(ym.getYear()))
                .param("mes", String.valueOf(ym.getMonthValue())))
            .andExpect(status().isOk())
            .andReturn();

        byte[] pdf = res.getResponse().getContentAsByteArray();
        assertTrue(pdf.length > 4);
        assertTrue(new String(pdf, 0, 4, StandardCharsets.US_ASCII).startsWith("%PDF"));

        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdf))) {
            String text = new PDFTextStripper().getText(doc);
            assertNotNull(text);
            assertFalse(text.contains("RuntimeException"));
            assertTrue(text.length() > 50);
        }
    }

    @Test
    void motorFinanceiro_semNan() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/motor-financeiro")
                .header("Authorization", "Bearer " + tokenA)
                .param("narrativa", "false"))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
        String raw = res.getResponse().getContentAsString();
        assertFalse(raw.contains("NaN"));
        assertFalse(raw.contains("Infinity"));
        assertNotNull(json.get("scoreExplicavel"));
    }

    @Test
    void registroDuplicado_retorna409() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        UsuarioDTO dto = new UsuarioDTO();
        dto.setUsername("dup_" + suffix);
        dto.setEmail("dup_" + suffix + "@test.local");
        dto.setPassword("SenhaTeste123!");
        dto.setNome("Dup");

        mockMvc.perform(post("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
            .andExpect(status().isOk());

        UsuarioDTO dup = new UsuarioDTO();
        dup.setUsername("outro_" + suffix);
        dup.setEmail(dto.getEmail());
        dup.setPassword("SenhaTeste123!");
        dup.setNome("Dup2");

        MvcResult conflict = mockMvc.perform(post("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dup)))
            .andExpect(status().isConflict())
            .andReturn();
        assertFalse(conflict.getResponse().getContentAsString().contains("uk_"));
    }

    private Usuario saveUser(String username, String email, String nome) {
        Usuario u = new Usuario();
        u.setUsername(username);
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode("SenhaTeste123!"));
        u.setNome(nome);
        return usuarioRepository.save(u);
    }

    private String bearerFor(Usuario usuario) {
        UserPrincipal principal = UserPrincipal.create(usuario);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return jwtTokenProvider.generateToken(auth);
    }
}
