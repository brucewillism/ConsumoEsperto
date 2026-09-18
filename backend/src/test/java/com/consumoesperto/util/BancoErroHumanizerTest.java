package com.consumoesperto.util;

import com.consumoesperto.exception.JarvisErrorCopy;
import org.hibernate.exception.SQLGrammarException;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BancoErroHumanizerTest {

    @Test
    void reconheceToastDeImportacaoComSqlGrammar() {
        String raw = "could not extract ResultSet; SQL [n/a]; nested exception is "
            + "org.hibernate.exception.SQLGrammarException: could not extract ResultSet";
        assertEquals(JarvisErrorCopy.SCHEMA_MISMATCH_MESSAGE, BancoErroHumanizer.humanizar(raw));
    }

    @Test
    void reconheceCadeiaHibernate() {
        SQLException pg = new SQLException("ERROR: column tipo_arquivo does not exist", "42703");
        SQLGrammarException hibernate = new SQLGrammarException("could not extract ResultSet", pg);
        String msg = BancoErroHumanizer.humanizar(hibernate);
        assertNotNull(msg);
        assertEquals(JarvisErrorCopy.SCHEMA_MISMATCH_MESSAGE, msg);
    }

    @Test
    void ignoraErroDeValidacao() {
        assertNull(BancoErroHumanizer.humanizar("O PDF parece ser um extrato de conta, não uma fatura de cartão."));
        assertNull(BancoErroHumanizer.humanizar((Throwable) null));
        assertNull(BancoErroHumanizer.humanizar(new IllegalArgumentException("cartão não encontrado")));
    }

    @Test
    void reconheceSqlExceptionGenericaDeColuna() {
        assertEquals(
            JarvisErrorCopy.SCHEMA_MISMATCH_MESSAGE,
            BancoErroHumanizer.humanizar(new SQLException("ERROR: column \"precisa_escolha_recurso\" does not exist"))
        );
    }
}
