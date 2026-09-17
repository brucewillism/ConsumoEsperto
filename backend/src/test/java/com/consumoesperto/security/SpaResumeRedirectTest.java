package com.consumoesperto.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpaResumeRedirectTest {

    @Test
    void loginViraQuerySegura() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/login");
        assertEquals("/?ce_resume=%2Flogin", SpaResumeRedirect.location(req));
    }

    @Test
    void rejeitaProtocoloEmCaminho() {
        assertFalse(SpaResumeRedirect.isSafeRelativePath("https://evil.example/phish"));
        assertFalse(SpaResumeRedirect.isSafeRelativePath("//evil.example"));
        assertTrue(SpaResumeRedirect.isSafeRelativePath("/transacoes?conta=1"));
    }

    @Test
    void naoEmpilhaCeResume() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/login");
        req.setQueryString("ce_resume=%2Fphishing&foo=1");
        assertEquals("/?ce_resume=%2Flogin%3Ffoo%3D1", SpaResumeRedirect.location(req));
    }

    @Test
    void raizSemResumeNaoGeraLoop() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/");
        req.setQueryString("ce_resume=%2Flogin");
        assertEquals("/", SpaResumeRedirect.location(req));
    }
}
