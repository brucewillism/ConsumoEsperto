package com.consumoesperto.integration;

import com.consumoesperto.mobilecapture.dto.CreateMobileCaptureDeviceRequest;
import com.consumoesperto.mobilecapture.dto.MobileDeviceRegistrationResponse;
import com.consumoesperto.mobilecapture.security.DeviceTokenHasher;
import com.consumoesperto.mobilecapture.security.MobileDeviceTokenFilter;
import com.consumoesperto.model.MobilePlatform;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.MobileCaptureDeviceRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.security.JwtTokenProvider;
import com.consumoesperto.security.UserPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MobileCaptureHttpIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UsuarioRepository usuarioRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private MobileCaptureDeviceRepository deviceRepository;
  @Autowired private TransacaoRepository transacaoRepository;

  private String tokenA;
  private String tokenB;
  private String deviceTokenA;
  private Long usuarioIdA;

  @BeforeEach
  void seed() throws Exception {
    String suffix = String.valueOf(System.nanoTime());
    Usuario a = saveUser("mob_a_" + suffix, "moba_" + suffix + "@test.local");
    Usuario b = saveUser("mob_b_" + suffix, "mobb_" + suffix + "@test.local");
    usuarioIdA = a.getId();
    tokenA = bearerFor(a);
    tokenB = bearerFor(b);

    CreateMobileCaptureDeviceRequest req = new CreateMobileCaptureDeviceRequest();
    req.setName("Android A");
    req.setPlatform(MobilePlatform.ANDROID_MACRODROID);
    MvcResult reg = mockMvc.perform(post("/api/mobile-capture/devices")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk())
        .andReturn();
    MobileDeviceRegistrationResponse response = objectMapper.readValue(
        reg.getResponse().getContentAsString(), MobileDeviceRegistrationResponse.class);
    deviceTokenA = response.getDeviceToken();
  }

  @Test
  void androidNotification_registraUmaTransacao() throws Exception {
    String payload = """
        {
          "source": "ANDROID_NOTIFICATION",
          "package": "com.nu.production",
          "notification_title": "Compra aprovada",
          "notification_text": "Compra de R$ 89,90 em POSTO SHELL"
        }
        """;
    MvcResult first = ingest(payload, "evt-android-1");
    JsonNode body = objectMapper.readTree(first.getResponse().getContentAsString());
    assertEquals("REGISTERED", body.get("status").asText());
    assertNotNull(body.get("transacaoId"));
    long countAfterFirst = transacaoRepository.findAll().stream()
        .filter(t -> usuarioIdA.equals(t.getUsuario().getId()))
        .count();
    assertEquals(1, countAfterFirst);

    MvcResult second = ingest(payload, "evt-android-1");
    JsonNode dup = objectMapper.readTree(second.getResponse().getContentAsString());
    assertEquals("DUPLICATE", dup.get("status").asText());
    long countAfterSecond = transacaoRepository.findAll().stream()
        .filter(t -> usuarioIdA.equals(t.getUsuario().getId()))
        .count();
    assertEquals(1, countAfterSecond);
  }

  @Test
  void iosWallet_registraUmaTransacao() throws Exception {
    String payload = """
        {
          "source": "IOS_WALLET",
          "amount": 89.90,
          "currency": "BRL",
          "merchant": "POSTO SHELL",
          "client_event_id": "ios-wallet-1"
        }
        """;
    MvcResult res = ingest(payload, "ios-wallet-1");
    JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
    assertEquals("REGISTERED", body.get("status").asText());
  }

  @Test
  void tokenInvalido_retorna401() throws Exception {
    mockMvc.perform(post("/api/ingestion/mobile/transactions")
            .header(MobileDeviceTokenFilter.DEVICE_TOKEN_HEADER, "ce_mcd_invalid")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"source\":\"TEST\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void usuarioB_naoRegistraDeviceDeA() throws Exception {
    CreateMobileCaptureDeviceRequest req = new CreateMobileCaptureDeviceRequest();
    req.setName("Hack");
    req.setPlatform(MobilePlatform.IOS_SHORTCUTS);
    mockMvc.perform(post("/api/mobile-capture/devices")
            .header("Authorization", "Bearer " + tokenB)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk());

    String payload = """
        {"source":"IOS_WALLET","amount":10.00,"merchant":"LOJA B","client_event_id":"ab-1"}
        """;
    ingestWithToken(payload, "ab-1", deviceTokenA);
    assertTrue(transacaoRepository.findAll().stream()
        .noneMatch(t -> t.getDescricao() != null && t.getDescricao().contains("LOJA B")
            && !usuarioIdA.equals(t.getUsuario().getId())));
  }

  @Test
  void testSource_naoCriaTransacao() throws Exception {
    MvcResult res = ingest("{\"source\":\"TEST\"}", null);
    JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
    assertEquals("REGISTERED", body.get("status").asText());
    assertTrue(body.get("message").asText().contains("TEST"));
    assertEquals(0, transacaoRepository.findAll().stream()
        .filter(t -> usuarioIdA.equals(t.getUsuario().getId())).count());
  }

  @Test
  void tokenHashNaoArmazenaPlaintext() {
    String hash = deviceRepository.findAll().stream()
        .filter(d -> d.getUsuario().getId().equals(usuarioIdA))
        .findFirst()
        .map(d -> d.getTokenHash())
        .orElseThrow();
    assertNotEquals(deviceTokenA, hash);
    assertEquals(DeviceTokenHasher.hashToken(deviceTokenA), hash);
  }

  private MvcResult ingest(String payload, String clientEventId) throws Exception {
    return ingestWithToken(payload, clientEventId, deviceTokenA);
  }

  private MvcResult ingestWithToken(String payload, String clientEventId, String deviceToken) throws Exception {
    var builder = post("/api/ingestion/mobile/transactions")
        .header(MobileDeviceTokenFilter.DEVICE_TOKEN_HEADER, deviceToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(payload);
    if (clientEventId != null) {
      builder.header(MobileDeviceTokenFilter.CLIENT_EVENT_HEADER, clientEventId);
    }
    return mockMvc.perform(builder).andExpect(status().isOk()).andReturn();
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
