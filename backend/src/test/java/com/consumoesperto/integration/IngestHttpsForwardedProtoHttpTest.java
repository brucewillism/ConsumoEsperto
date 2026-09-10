package com.consumoesperto.integration;

import com.consumoesperto.mobilecapture.dto.CreateMobileCaptureDeviceRequest;
import com.consumoesperto.mobilecapture.dto.MobileDeviceRegistrationResponse;
import com.consumoesperto.mobilecapture.security.MobileDeviceTokenFilter;
import com.consumoesperto.model.MobilePlatform;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.security.ForwardedHttps;
import com.consumoesperto.security.JwtTokenProvider;
import com.consumoesperto.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "consumoesperto.mobile-capture.enabled=true",
    "consumoesperto.mobile-capture.require-https=true",
    "consumoesperto.ingest.notificacao.enabled=true",
    "consumoesperto.ingest.notificacao.require-https=true",
    "server.forward-headers-strategy=NONE"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IngestHttpsForwardedProtoHttpTest {

    private static final String IOS_BODY = """
        {
          "source": "IOS_WALLET",
          "amount": 45.90,
          "currency": "BRL",
          "merchant": "PADARIA DO ZE",
          "occurred_at": "2026-09-09T11:13:00-03:00",
          "card_hint": "Nubank"
        }
        """;

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private String deviceToken;

    @BeforeEach
    void seed() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        Usuario u = new Usuario();
        u.setUsername("https_ing_" + suffix);
        u.setEmail("https_ing_" + suffix + "@test.local");
        u.setPassword(passwordEncoder.encode("SenhaTeste123!"));
        u.setNome("Https");
        u = usuarioRepository.save(u);

        CreateMobileCaptureDeviceRequest req = new CreateMobileCaptureDeviceRequest();
        req.setName("iPhone");
        req.setPlatform(MobilePlatform.IOS_SHORTCUTS);
        UserPrincipal principal = UserPrincipal.create(u);
        String jwt = jwtTokenProvider.generateToken(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        MvcResult reg = mockMvc.perform(post("/api/mobile-capture/devices")
                .header("Authorization", "Bearer " + jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andReturn();
        deviceToken = objectMapper.readValue(
            reg.getResponse().getContentAsString(), MobileDeviceRegistrationResponse.class)
            .getDeviceToken();
    }

    @Test
    void semForwardedProto_retorna403ComInstrucao() throws Exception {
        mockMvc.perform(post("/api/ingestion/mobile/transactions")
                .header(MobileDeviceTokenFilter.DEVICE_TOKEN_HEADER, deviceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(IOS_BODY))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value(ForwardedHttps.ERROR_CODE))
            .andExpect(jsonPath("$.instrucao").isNotEmpty())
            .andExpect(jsonPath("$.details.scheme").value("http"));
    }

    @Test
    void xForwardedProtoHttps_passaNaVerificacaoERegista() throws Exception {
        mockMvc.perform(post("/api/ingestion/mobile/transactions")
                .header(MobileDeviceTokenFilter.DEVICE_TOKEN_HEADER, deviceToken)
                .header("X-Forwarded-Proto", "https")
                .contentType(MediaType.APPLICATION_JSON)
                .content(IOS_BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REGISTERED"));
    }

    @Test
    void xForwardedProtoListaHttpsHttp_passaNaVerificacao() throws Exception {
        mockMvc.perform(post("/api/ingestion/mobile/transactions")
                .header(MobileDeviceTokenFilter.DEVICE_TOKEN_HEADER, deviceToken)
                .header("X-Forwarded-Proto", "https, http")
                .contentType(MediaType.APPLICATION_JSON)
                .content(IOS_BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REGISTERED"));
    }

    @Test
    void ingestNotificacao_comForwardedProtoHttps_naoRetorna403() throws Exception {
        mockMvc.perform(post("/api/ingest/notificacao")
                .header("X-Forwarded-Proto", "https")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"origem\":\"IOS_ATALHOS\",\"app\":\"nubank\",\"texto\":\"x\"}"))
            .andExpect(status().isUnauthorized());
    }
}
