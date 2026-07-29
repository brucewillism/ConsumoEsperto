package com.consumoesperto.integration;

import com.consumoesperto.dto.UsuarioDTO;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RegistroDuplicadoHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private UsuarioDTO dto(String suffix) {
        UsuarioDTO dto = new UsuarioDTO();
        dto.setUsername("user_" + suffix + "@test.local");
        dto.setEmail("user_" + suffix + "@test.local");
        dto.setPassword("SenhaTeste123!");
        dto.setNome("Usuario " + suffix);
        return dto;
    }

    @Test
    void primeiroRegistro_retorna200() throws Exception {
        mockMvc.perform(post("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto("a1"))))
            .andExpect(status().isOk());
    }

    @Test
    void emailDuplicado_retorna409() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        UsuarioDTO first = dto(suffix);
        mockMvc.perform(post("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(first)))
            .andExpect(status().isOk());

        UsuarioDTO dup = dto("x" + suffix);
        dup.setUsername("outro_" + suffix + "@test.local");
        dup.setEmail(first.getEmail());
        mockMvc.perform(post("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dup)))
            .andExpect(status().isConflict());
    }

    @Test
    void emailDuplicadoSegundaTentativa_retorna409() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        UsuarioDTO first = dto(suffix);
        mockMvc.perform(post("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(first)))
            .andExpect(status().isOk());

        UsuarioDTO dup = dto("y" + suffix);
        dup.setUsername("outro2_" + suffix + "@test.local");
        dup.setEmail(first.getEmail());
        mockMvc.perform(post("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dup)))
            .andExpect(status().isConflict());
    }

    @Test
    void emailComMaiusculasDuplicado_retorna409() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        UsuarioDTO first = dto(suffix);
        mockMvc.perform(post("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(first)))
            .andExpect(status().isOk());

        UsuarioDTO dup = dto("m" + suffix);
        dup.setUsername("outro4_" + suffix + "@test.local");
        dup.setEmail(first.getEmail().toUpperCase());
        mockMvc.perform(post("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dup)))
            .andExpect(status().isConflict());
    }

    @Test
    void emailComEspacosDuplicado_retorna409() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        UsuarioDTO first = dto(suffix);
        mockMvc.perform(post("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(first)))
            .andExpect(status().isOk());

        UsuarioDTO dup = dto("z" + suffix);
        dup.setUsername("outro3_" + suffix + "@test.local");
        dup.setEmail("  " + first.getEmail().toUpperCase() + "  ");
        mockMvc.perform(post("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dup)))
            .andExpect(status().isConflict());
    }

    @Test
    void usernameDuplicado_retorna409() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        Usuario existente = new Usuario();
        existente.setUsername("fixo_" + suffix);
        existente.setEmail("outro_" + suffix + "@test.local");
        existente.setPassword(passwordEncoder.encode("SenhaTeste123!"));
        existente.setNome("Existente");
        usuarioRepository.save(existente);

        UsuarioDTO novo = dto("x" + suffix);
        novo.setUsername("fixo_" + suffix);
        novo.setEmail("novo_" + suffix + "@test.local");
        mockMvc.perform(post("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(novo)))
            .andExpect(status().isConflict());
    }

    @Test
    void segundoRegistroMesmoPayload_retorna409() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        UsuarioDTO payload = dto("dup2_" + suffix);
        mockMvc.perform(post("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
            .andExpect(status().isConflict());
    }

    @Test
    void corpoErro409_contemCodigoSeguro() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        mockMvc.perform(post("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto("safe_" + suffix))))
            .andExpect(status().isOk());

        MvcResult res = mockMvc.perform(post("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto("safe_" + suffix))))
            .andExpect(status().isConflict())
            .andReturn();
        JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
        assertEquals("DUPLICATE_RECORD", json.get("error").asText());
        assertTrue(json.has("message"));
    }
}
