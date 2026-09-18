package com.consumoesperto.util;

import com.consumoesperto.exception.JarvisErrorCopy;

/**
 * Mensagem de utilizador quando o Hibernate/Postgres recusa SQL por schema desatualizado.
 */
public final class BancoErroHumanizer {

    private BancoErroHumanizer() {
    }

    public static String humanizar(Throwable error) {
        if (error == null) {
            return null;
        }
        StringBuilder joined = new StringBuilder();
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t.getMessage() != null) {
                joined.append(t.getMessage()).append('\n');
            }
            joined.append(t.getClass().getName()).append('\n');
        }
        return humanizar(joined.toString());
    }

    /**
     * @return texto para o utilizador ou {@code null} se não for falha de SQL/schema
     */
    public static String humanizar(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String n = raw.toLowerCase();
        boolean schema = n.contains("sqlgrammarexception")
            || n.contains("could not extract resultset")
            || n.contains("could not execute statement")
            || n.contains("bad sql grammar")
            || n.contains("psqlexception")
            || n.contains("undefined_column")
            || n.contains("undefined_table")
            || (n.contains("column") && n.contains("does not exist"))
            || (n.contains("relation") && n.contains("does not exist"))
            || n.contains("invaliddataaccessresourceusageexception");
        return schema ? JarvisErrorCopy.SCHEMA_MISMATCH_MESSAGE : null;
    }
}
