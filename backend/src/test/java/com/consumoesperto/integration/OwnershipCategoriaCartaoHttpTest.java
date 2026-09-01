package com.consumoesperto.integration;

import com.consumoesperto.dto.CompraParceladaDTO;
import com.consumoesperto.exception.ResourceNotFoundException;
import com.consumoesperto.model.Categoria;
import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.CompraParcelada;
import com.consumoesperto.model.Fatura;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.CategoriaRepository;
import com.consumoesperto.repository.CompraParceladaRepository;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.security.JwtTokenProvider;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.CompraParceladaService;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ownership (anti-IDOR) de categoria em transações e de cartão em faturas.
 * Política do projeto: recurso alheio ou inexistente → 404; recurso inativo → 400. Nunca 500.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OwnershipCategoriaCartaoHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private CartaoCreditoRepository cartaoCreditoRepository;
    @Autowired private FaturaRepository faturaRepository;
    @Autowired private CompraParceladaRepository compraParceladaRepository;
    @Autowired private CompraParceladaService compraParceladaService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private Usuario userA;
    private Usuario userB;
    private String tokenA;
    private Long categoriaAId;
    private Long categoriaBId;
    private Long categoriaInativaAId;
    private Long cartaoAId;
    private Long cartaoBId;
    private Long cartaoInativoAId;

    @BeforeEach
    void seed() {
        String sfx = String.valueOf(System.nanoTime());
        userA = saveUser("own_a_" + sfx, "own_a_" + sfx + "@test.local");
        userB = saveUser("own_b_" + sfx, "own_b_" + sfx + "@test.local");
        tokenA = bearerFor(userA);

        categoriaAId = saveCategoria(userA, "Mercado A", true).getId();
        categoriaBId = saveCategoria(userB, "Mercado B", true).getId();
        categoriaInativaAId = saveCategoria(userA, "Antiga A", false).getId();

        cartaoAId = saveCartao(userA, "Cartão A", true).getId();
        cartaoBId = saveCartao(userB, "Cartão B", true).getId();
        cartaoInativoAId = saveCartao(userA, "Cartão Antigo A", false).getId();
    }

    // ------------------------------------------------------------------
    // Categoria em transações
    // ------------------------------------------------------------------

    @Test
    void criarTransacao_categoriaPropria_ok() throws Exception {
        mockMvc.perform(post("/api/transacoes")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(transacaoJson(categoriaAId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categoriaId").value(categoriaAId));
    }

    @Test
    void criarTransacao_categoriaDeOutroUsuario_retorna404() throws Exception {
        mockMvc.perform(post("/api/transacoes")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(transacaoJson(categoriaBId)))
            .andExpect(status().isNotFound());
    }

    @Test
    void criarTransacao_categoriaInexistente_retorna404() throws Exception {
        mockMvc.perform(post("/api/transacoes")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(transacaoJson(999_999_999L)))
            .andExpect(status().isNotFound());
    }

    @Test
    void criarTransacao_categoriaInativa_retorna400() throws Exception {
        mockMvc.perform(post("/api/transacoes")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(transacaoJson(categoriaInativaAId)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void editarTransacao_trocandoParaCategoriaAlheia_retorna404() throws Exception {
        String criada = mockMvc.perform(post("/api/transacoes")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(transacaoJson(categoriaAId)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        long transacaoId = com.jayway.jsonpath.JsonPath.parse(criada).read("$.id", Long.class);

        mockMvc.perform(put("/api/transacoes/" + transacaoId)
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(transacaoJson(categoriaBId)))
            .andExpect(status().isNotFound());
    }

    @Test
    void criarTransacaoParcelada_categoriaAlheia_retorna404() throws Exception {
        // Descrição sem "/" — o @Pattern do DTO não aceita barra
        String json = "{"
            + "\"descricao\":\"Parcela 1 de 3\","
            + "\"valor\":50.00,"
            + "\"tipoTransacao\":\"DESPESA\","
            + "\"dataTransacao\":\"" + LocalDateTime.now() + "\","
            + "\"statusConferencia\":\"PENDENTE\","
            + "\"parcelaAtual\":1,"
            + "\"totalParcelas\":3,"
            + "\"categoriaId\":" + categoriaBId
            + "}";
        mockMvc.perform(post("/api/transacoes")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isNotFound());
    }

    /**
     * Cobre também os fluxos que passam pelo serviço sem HTTP (importação, WhatsApp,
     * agendamento): a atualização de compra parcelada não pode aceitar categoria alheia.
     */
    @Test
    void atualizarCompraParcelada_categoriaAlheia_lancaNotFound() {
        CompraParcelada compra = new CompraParcelada();
        compra.setDescricao("Compra A");
        compra.setValorTotal(new BigDecimal("300.00"));
        compra.setValorParcela(new BigDecimal("100.00"));
        compra.setNumeroParcelas(3);
        compra.setParcelaAtual(1);
        compra.setDataCompra(LocalDateTime.now());
        compra.setDataPrimeiraParcela(LocalDateTime.now());
        compra.setDataUltimaParcela(LocalDateTime.now().plusMonths(2));
        compra.setCartaoCredito(cartaoCreditoRepository.findById(cartaoAId).orElseThrow());
        compra.setUsuario(userA);
        Long compraId = compraParceladaRepository.save(compra).getId();

        CompraParceladaDTO dto = new CompraParceladaDTO();
        dto.setDescricao("Compra A");
        dto.setValorTotal(new BigDecimal("300.00"));
        dto.setValorParcela(new BigDecimal("100.00"));
        dto.setNumeroParcelas(3);
        dto.setParcelaAtual(1);
        dto.setDataCompra(LocalDateTime.now());
        dto.setDataPrimeiraParcela(LocalDateTime.now());
        dto.setDataUltimaParcela(LocalDateTime.now().plusMonths(2));
        dto.setCategoriaId(categoriaBId);

        assertThrows(ResourceNotFoundException.class,
            () -> compraParceladaService.atualizarCompraParcelada(compraId, dto, userA.getId()));
    }

    // ------------------------------------------------------------------
    // Cartão em faturas
    // ------------------------------------------------------------------

    @Test
    void criarFatura_cartaoProprio_ok() throws Exception {
        mockMvc.perform(post("/api/faturas")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(faturaJson(cartaoAId)))
            .andExpect(status().isOk());
    }

    @Test
    void criarFatura_cartaoDeOutroUsuario_retorna404() throws Exception {
        mockMvc.perform(post("/api/faturas")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(faturaJson(cartaoBId)))
            .andExpect(status().isNotFound());
    }

    @Test
    void criarFatura_cartaoInexistente_retorna404() throws Exception {
        mockMvc.perform(post("/api/faturas")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(faturaJson(999_999_999L)))
            .andExpect(status().isNotFound());
    }

    @Test
    void criarFatura_cartaoInativo_retorna400() throws Exception {
        mockMvc.perform(post("/api/faturas")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(faturaJson(cartaoInativoAId)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void listarFaturasPorCartaoAlheio_retorna4xxSemDados() throws Exception {
        mockMvc.perform(get("/api/faturas/cartao/" + cartaoBId)
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void pagarFaturaDeOutroUsuario_retorna4xx() throws Exception {
        Fatura faturaB = new Fatura();
        faturaB.setCartaoCredito(cartaoCreditoRepository.findById(cartaoBId).orElseThrow());
        faturaB.setUsuario(userB);
        faturaB.setStatusFatura(Fatura.StatusFatura.ABERTA);
        faturaB.setDataVencimento(LocalDateTime.now().plusDays(10));
        faturaB.setDataFechamento(LocalDateTime.now());
        faturaB.setValorFatura(new BigDecimal("100.00"));
        faturaB.setValorTotal(new BigDecimal("100.00"));
        faturaB.setValorMinimo(new BigDecimal("10.00"));
        faturaB.setPaga(false);
        faturaB.setNumeroFatura("FAT-B-" + System.nanoTime());
        Long faturaBId = faturaRepository.save(faturaB).getId();

        mockMvc.perform(post("/api/faturas/pagar")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"faturaId\":" + faturaBId + ",\"contaBancariaId\":1}"))
            .andExpect(status().is4xxClientError());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String transacaoJson(Long categoriaId) {
        return "{"
            + "\"descricao\":\"Compra teste\","
            + "\"valor\":10.50,"
            + "\"tipoTransacao\":\"DESPESA\","
            + "\"dataTransacao\":\"" + LocalDateTime.now() + "\","
            + "\"statusConferencia\":\"PENDENTE\","
            + "\"categoriaId\":" + categoriaId
            + "}";
    }

    private String faturaJson(Long cartaoId) {
        LocalDateTime venc = LocalDateTime.now().plusDays(20);
        return "{"
            + "\"cartaoCreditoId\":" + cartaoId + ","
            + "\"dataVencimento\":\"" + venc + "\","
            + "\"dataFechamento\":\"" + venc.minusDays(10) + "\","
            + "\"valorFatura\":0,"
            + "\"numeroFatura\":\"TESTE-" + System.nanoTime() + "\""
            + "}";
    }

    private Usuario saveUser(String username, String email) {
        Usuario u = new Usuario();
        u.setUsername(username);
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode("SenhaTeste123!"));
        u.setNome("Test " + username);
        return usuarioRepository.save(u);
    }

    private Categoria saveCategoria(Usuario dono, String nome, boolean ativa) {
        Categoria c = new Categoria();
        c.setNome(nome + "_" + System.nanoTime());
        c.setUsuario(dono);
        c.setAtivo(ativa);
        return categoriaRepository.save(c);
    }

    private CartaoCredito saveCartao(Usuario dono, String nome, boolean ativo) {
        CartaoCredito cartao = new CartaoCredito();
        cartao.setNome(nome);
        cartao.setBanco("Banco Teste");
        cartao.setNumeroCartao(String.valueOf(System.nanoTime()).substring(0, 4));
        cartao.setLimiteCredito(new BigDecimal("5000.00"));
        cartao.setLimiteDisponivel(new BigDecimal("5000.00"));
        cartao.setDiaVencimento(10);
        cartao.setAtivo(ativo);
        cartao.setUsuario(dono);
        return cartaoCreditoRepository.save(cartao);
    }

    private String bearerFor(Usuario usuario) {
        UserPrincipal principal = UserPrincipal.create(usuario);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return jwtTokenProvider.generateToken(auth);
    }
}
