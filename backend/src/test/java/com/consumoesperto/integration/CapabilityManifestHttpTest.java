package com.consumoesperto.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = "consumoesperto.edith.callback-secret=test-callback-secret")
class CapabilityManifestHttpTest {

    @Autowired MockMvc mockMvc;

    @Test
    void semSegredoE401() throws Exception {
        mockMvc.perform(get("/api/capabilities"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void comSegredoRetornaFinanceiroFinancial() throws Exception {
        mockMvc.perform(get("/api/capabilities").header("X-Eco-Service-Key", "test-callback-secret"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provider").value("consumo-esperto"))
            .andExpect(jsonPath("$.capabilities[0].sensitivity").value("FINANCIAL"))
            .andExpect(jsonPath("$.capabilities[*].sensitivity").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.equalTo("FINANCIAL"))))
            .andExpect(jsonPath("$.capabilities[?(@.id=='finance.month.summary')].execution").value(org.hamcrest.Matchers.hasItem("DETERMINISTIC")))
            .andExpect(jsonPath("$.capabilities[?(@.id=='finance.accounts.list')]").exists())
            .andExpect(jsonPath("$.capabilities[?(@.id=='finance.transactions.search')]").exists())
            .andExpect(jsonPath("$.capabilities[?(@.id=='finance.invoice.read')]").exists())
            .andExpect(jsonPath("$.capabilities[?(@.id=='finance.cards.list')]").exists());
    }

    @Test
    void jwtDeUsuarioNaoSubstituiSegredo() throws Exception {
        mockMvc.perform(get("/capabilities").header("Authorization", "Bearer not-a-service-secret"))
            .andExpect(status().isUnauthorized());
    }
}
