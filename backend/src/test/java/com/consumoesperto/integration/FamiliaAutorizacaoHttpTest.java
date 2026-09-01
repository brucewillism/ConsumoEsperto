package com.consumoesperto.integration;

import com.consumoesperto.model.GrupoFamiliarMembro;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.GrupoFamiliarMembroRepository;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matriz de autorização do módulo Família (OWNER / MEMBER / fora do grupo).
 *
 * | Ação             | Owner | Member | Fora |
 * | Ver grupo        |  200  |  200   | 204  |
 * | Renomear         |  200  |  403   | 4xx  |
 * | Convidar         |  200  |  403   | 4xx  |
 * | Cancelar convite |  200  |  403   | 4xx  |
 * | Remover membro   |  200  |  403   | 4xx  |
 * | Sair             |  (¹)  |  204   | 4xx  |
 * | Orçto compart.   |  200  |  200   | 200 vazio |
 * | Racha (balanço)  |  200  |  200   | 200 vazio |
 *
 * (¹) OWNER só sai/encerra depois de remover os demais membros.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FamiliaAutorizacaoHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private GrupoFamiliarMembroRepository membroRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private Usuario owner;
    private Usuario member;
    private Usuario fora;
    private String tokenOwner;
    private String tokenMember;
    private String tokenFora;
    private Long grupoId;
    private Long membroAceitoId;

    @BeforeEach
    void seed() throws Exception {
        String sfx = String.valueOf(System.nanoTime());
        owner = saveUser("fam_owner_" + sfx, "fam_owner_" + sfx + "@test.local");
        member = saveUser("fam_member_" + sfx, "fam_member_" + sfx + "@test.local");
        fora = saveUser("fam_fora_" + sfx, "fam_fora_" + sfx + "@test.local");
        tokenOwner = bearerFor(owner);
        tokenMember = bearerFor(member);
        tokenFora = bearerFor(fora);

        // owner cria grupo
        mockMvc.perform(post("/api/familia")
                .header("Authorization", "Bearer " + tokenOwner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Familia Teste\"}"))
            .andExpect(status().isOk());

        // owner convida member por e-mail
        mockMvc.perform(post("/api/familia/convites")
                .header("Authorization", "Bearer " + tokenOwner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + member.getEmail() + "\"}"))
            .andExpect(status().isOk());

        // member aceita
        Long conviteId = conviteIdPendenteDe(member);
        mockMvc.perform(post("/api/familia/convites/" + conviteId + "/responder")
                .header("Authorization", "Bearer " + tokenMember)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"aceitar\":true}"))
            .andExpect(status().isOk());

        GrupoFamiliarMembro membroAceito = membroRepository.findAceitosByUsuarioId(member.getId()).get(0);
        grupoId = membroAceito.getGrupoFamiliar().getId();
        membroAceitoId = membroAceito.getId();
    }

    // ------------------------------------------------------------------
    // Ver grupo
    // ------------------------------------------------------------------

    @Test
    void verGrupo_ownerEMember200_fora204() throws Exception {
        mockMvc.perform(get("/api/familia").header("Authorization", "Bearer " + tokenOwner))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/familia").header("Authorization", "Bearer " + tokenMember))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/familia").header("Authorization", "Bearer " + tokenFora))
            .andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------------
    // Renomear
    // ------------------------------------------------------------------

    @Test
    void renomear_somenteOwner() throws Exception {
        mockMvc.perform(put("/api/familia")
                .header("Authorization", "Bearer " + tokenOwner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Novo Nome\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(put("/api/familia")
                .header("Authorization", "Bearer " + tokenMember)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Hack\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/familia")
                .header("Authorization", "Bearer " + tokenFora)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Hack\"}"))
            .andExpect(status().is4xxClientError());
    }

    // ------------------------------------------------------------------
    // Convidar
    // ------------------------------------------------------------------

    @Test
    void convidar_somenteOwner() throws Exception {
        mockMvc.perform(post("/api/familia/convites")
                .header("Authorization", "Bearer " + tokenOwner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"novo@test.local\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/familia/convites")
                .header("Authorization", "Bearer " + tokenMember)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"outro@test.local\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/familia/convites")
                .header("Authorization", "Bearer " + tokenFora)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"x@test.local\"}"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void conviteDuplicadoAtivo_ehRejeitado() throws Exception {
        mockMvc.perform(post("/api/familia/convites")
                .header("Authorization", "Bearer " + tokenOwner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dup@test.local\"}"))
            .andExpect(status().isOk());
        // Handler global do projeto mapeia duplicidade para 409 (DUPLICATE_OR_CONFLICT)
        mockMvc.perform(post("/api/familia/convites")
                .header("Authorization", "Bearer " + tokenOwner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dup@test.local\"}"))
            .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------
    // Cancelar convite
    // ------------------------------------------------------------------

    @Test
    void cancelarConvite_somenteOwner() throws Exception {
        Long conviteId = criarConvitePendente("cancelar@test.local");
        mockMvc.perform(delete("/api/familia/convites/" + conviteId)
                .header("Authorization", "Bearer " + tokenMember))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/familia/convites/" + conviteId)
                .header("Authorization", "Bearer " + tokenOwner))
            .andExpect(status().isOk());
        // cancelado não pode ser aceito
        GrupoFamiliarMembro convite = membroRepository.findById(conviteId).orElseThrow();
        assertEquals(GrupoFamiliarMembro.Status.CANCELADO, convite.getStatus());
    }

    // ------------------------------------------------------------------
    // Remover membro
    // ------------------------------------------------------------------

    @Test
    void removerMembro_somenteOwner() throws Exception {
        mockMvc.perform(delete("/api/familia/membros/" + membroAceitoId)
                .header("Authorization", "Bearer " + tokenMember))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/familia/membros/" + membroAceitoId)
                .header("Authorization", "Bearer " + tokenOwner))
            .andExpect(status().isOk());
        GrupoFamiliarMembro removido = membroRepository.findById(membroAceitoId).orElseThrow();
        assertEquals(GrupoFamiliarMembro.Status.CANCELADO, removido.getStatus());
    }

    // ------------------------------------------------------------------
    // Sair
    // ------------------------------------------------------------------

    @Test
    void member_podeSair() throws Exception {
        mockMvc.perform(delete("/api/familia/sair")
                .header("Authorization", "Bearer " + tokenMember))
            .andExpect(status().isNoContent());
    }

    @Test
    void owner_naoSaiComMembrosAtivos() throws Exception {
        mockMvc.perform(delete("/api/familia/sair")
                .header("Authorization", "Bearer " + tokenOwner))
            .andExpect(status().isBadRequest());
    }

    @Test
    void owner_encerraGrupoQuandoUltimoMembro() throws Exception {
        mockMvc.perform(delete("/api/familia/membros/" + membroAceitoId)
                .header("Authorization", "Bearer " + tokenOwner))
            .andExpect(status().isOk());
        mockMvc.perform(delete("/api/familia/sair")
                .header("Authorization", "Bearer " + tokenOwner))
            .andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------------
    // Convites: identidade, reutilização, expiração
    // ------------------------------------------------------------------

    @Test
    void conviteNaoPodeSerAceitoPorOutroUsuario() throws Exception {
        Long conviteId = criarConvitePendente("dono-do-convite@test.local");
        mockMvc.perform(post("/api/familia/convites/" + conviteId + "/responder")
                .header("Authorization", "Bearer " + tokenFora)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"aceitar\":true}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void conviteRespondido_naoPodeSerReutilizado() throws Exception {
        Long conviteId = criarConvitePendente(fora.getEmail());
        mockMvc.perform(post("/api/familia/convites/" + conviteId + "/responder")
                .header("Authorization", "Bearer " + tokenFora)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"aceitar\":false}"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/familia/convites/" + conviteId + "/responder")
                .header("Authorization", "Bearer " + tokenFora)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"aceitar\":true}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void conviteExpirado_naoPodeSerAceito() throws Exception {
        Long conviteId = criarConvitePendente(fora.getEmail());
        GrupoFamiliarMembro convite = membroRepository.findById(conviteId).orElseThrow();
        convite.setDataConvite(LocalDateTime.now().minusDays(30));
        membroRepository.save(convite);

        mockMvc.perform(post("/api/familia/convites/" + conviteId + "/responder")
                .header("Authorization", "Bearer " + tokenFora)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"aceitar\":true}"))
            .andExpect(status().isBadRequest());
        assertEquals(GrupoFamiliarMembro.Status.EXPIRADO,
            membroRepository.findById(conviteId).orElseThrow().getStatus());
    }

    // ------------------------------------------------------------------
    // Orçamento compartilhado / racha / privacidade
    // ------------------------------------------------------------------

    @Test
    void orcamentoCompartilhadoERacha_membrosAcessam_foraNaoVeDados() throws Exception {
        mockMvc.perform(get("/api/familia/orcamentos-compartilhados")
                .header("Authorization", "Bearer " + tokenOwner))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/familia/orcamentos-compartilhados")
                .header("Authorization", "Bearer " + tokenMember))
            .andExpect(status().isOk());
        MvcResult res = mockMvc.perform(get("/api/familia/orcamentos-compartilhados")
                .header("Authorization", "Bearer " + tokenFora))
            .andExpect(status().isOk())
            .andReturn();
        assertEquals("[]", res.getResponse().getContentAsString().trim(),
            "Usuário fora do grupo não vê orçamentos compartilhados");

        mockMvc.perform(get("/api/familia/balanco")
                .header("Authorization", "Bearer " + tokenMember))
            .andExpect(status().isOk());
    }

    @Test
    void membroNaoRecebeTransacoesPrivadasDeOutroMembro() throws Exception {
        // owner cria transação privada
        mockMvc.perform(post("/api/transacoes")
                .header("Authorization", "Bearer " + tokenOwner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"descricao\":\"Segredo do Owner\",\"valor\":10,"
                    + "\"tipoTransacao\":\"DESPESA\",\"dataTransacao\":\"" + LocalDateTime.now() + "\","
                    + "\"statusConferencia\":\"PENDENTE\"}"))
            .andExpect(status().isOk());

        MvcResult res = mockMvc.perform(get("/api/transacoes")
                .header("Authorization", "Bearer " + tokenMember))
            .andExpect(status().isOk())
            .andReturn();
        assertFalse(res.getResponse().getContentAsString().contains("Segredo do Owner"),
            "Membro do grupo não pode ver transações privadas de outro membro");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Long criarConvitePendente(String email) throws Exception {
        mockMvc.perform(post("/api/familia/convites")
                .header("Authorization", "Bearer " + tokenOwner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"))
            .andExpect(status().isOk());
        List<GrupoFamiliarMembro> membros = membroRepository.findByGrupoFamiliarIdFetchUsuario(grupoId);
        return membros.stream()
            .filter(m -> m.getStatus() == GrupoFamiliarMembro.Status.PENDENTE)
            .filter(m -> email.equalsIgnoreCase(m.getConviteEmail()))
            .findFirst()
            .orElseThrow()
            .getId();
    }

    private Long conviteIdPendenteDe(Usuario usuario) {
        return membroRepository.findPendentesParaIdentidade(
                usuario.getEmail(), usuario.getWhatsappNumero() != null ? usuario.getWhatsappNumero() : "")
            .get(0).getId();
    }

    private Usuario saveUser(String username, String email) {
        Usuario u = new Usuario();
        u.setUsername(username);
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode("SenhaTeste123!"));
        u.setNome("Test " + username);
        return usuarioRepository.save(u);
    }

    private String bearerFor(Usuario usuario) {
        UserPrincipal principal = UserPrincipal.create(usuario);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return jwtTokenProvider.generateToken(auth);
    }
}
