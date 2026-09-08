package com.consumoesperto.eco;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EcoUlidTest {

    @Test
    void geraUlidCrockfordComPrefixo() {
        String trace = EcoUlid.trace();
        String span = EcoUlid.span();
        assertTrue(trace.startsWith("tr_"));
        assertTrue(span.startsWith("sp_"));
        assertEquals(3 + 26, trace.length());
        assertEquals(3 + 26, span.length());
        assertEquals(26, EcoUlid.ulid().length());
    }

    @Test
    void userIdNumerico() {
        assertEquals("usr_7", EcoUlid.user(7L));
    }
}
