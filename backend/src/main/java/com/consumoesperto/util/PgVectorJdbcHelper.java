package com.consumoesperto.util;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Detecção runtime do tipo da coluna {@code embedding}. Sem extensão pgvector o schema fallback usa {@code BYTEA}
 * (sem operador {@code <=>}); estas consultas evitam queries que quebram no Postgres “vanilla”.
 */
public final class PgVectorJdbcHelper {

    private PgVectorJdbcHelper() {
    }

    /**
     * Verifica se {@code schema.table.column} existe e tem UDT {@code vector} (pgvector).
     *
     * @return false se tabela/coluna não existirem ou forem {@code BYTEA}/outros.
     */
    public static boolean isVectorEmbeddingColumn(JdbcTemplate jdbcTemplate, String schema, String table, String column) {
        if (jdbcTemplate == null || schema == null || table == null || column == null) {
            return false;
        }
        try {
            String udt = jdbcTemplate.queryForObject(
                "SELECT c.udt_name FROM information_schema.columns c "
                    + "WHERE c.table_schema = ? AND c.table_name = ? AND c.column_name = ?",
                String.class,
                schema.trim(),
                table.trim(),
                column.trim());
            return udt != null && "vector".equalsIgnoreCase(udt.trim());
        } catch (EmptyResultDataAccessException ignored) {
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }
}
