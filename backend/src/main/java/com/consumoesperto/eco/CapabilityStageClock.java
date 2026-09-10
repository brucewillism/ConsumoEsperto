package com.consumoesperto.eco;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Relógio de estágios da capability. Desligado em produção (boolean no ThreadLocal).
 * Nomes canônicos do contrato ({@code t_tool_ms}, {@code t_total_ms}, {@code t_unaccounted_ms})
 * mais sufixos desta rodada ({@code t_pool_acquire_ms}, {@code t_sql_ids_ms}, …).
 * Não altera o caminho quando {@link #enabled()} é falso.
 */
public final class CapabilityStageClock {

    public static final String POOL = "t_pool_acquire_ms";
    public static final String OWNERSHIP = "t_ownership_ms";
    public static final String SQL_IDS = "t_sql_ids_ms";
    public static final String JPA_HYDRATE = "t_jpa_hydrate_ms";
    public static final String JPA_ITEMS = "t_jpa_items_ms";
    public static final String RECONCILE = "t_reconcile_ms";
    public static final String DTO_MAP = "t_dto_map_ms";
    public static final String JSON = "t_json_ms";
    public static final String FRAMEWORK = "t_framework_ms";
    public static final String TOOL = "t_tool_ms";
    public static final String TOTAL = "t_total_ms";
    public static final String UNACCOUNTED = "t_unaccounted_ms";

    private static final ThreadLocal<Boolean> ON = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<LinkedHashMap<String, Long>> NANOS =
        ThreadLocal.withInitial(LinkedHashMap::new);

    private CapabilityStageClock() {
    }

    public static void enable() {
        ON.set(Boolean.TRUE);
        NANOS.set(new LinkedHashMap<>());
    }

    public static void disable() {
        ON.remove();
        NANOS.remove();
    }

    public static boolean enabled() {
        return Boolean.TRUE.equals(ON.get());
    }

    public static void addNanos(String name, long nanos) {
        if (!enabled() || name == null) {
            return;
        }
        NANOS.get().merge(name, nanos, Long::sum);
    }

    public static <T> T timed(String name, Supplier<T> work) {
        if (!enabled()) {
            return work.get();
        }
        long t0 = System.nanoTime();
        try {
            return work.get();
        } finally {
            addNanos(name, System.nanoTime() - t0);
        }
    }

    public static void timed(String name, Runnable work) {
        timed(name, () -> {
            work.run();
            return null;
        });
    }

    /**
     * Sonda o pool (get + close). Só com o relógio ligado — não é o caminho de produção.
     * Conexão aparte da do JPA, para não vazar fora de transação.
     */
    public static void acquirePool(DataSource dataSource) {
        if (!enabled() || dataSource == null) {
            return;
        }
        timed(POOL, () -> {
            try (Connection c = dataSource.getConnection()) {
                return c;
            } catch (Exception e) {
                return null;
            }
        });
    }

    /** Snapshot em milissegundos (fração, para estágios &lt; 1 ms). */
    public static Map<String, Double> snapshotMs() {
        Map<String, Double> out = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : NANOS.get().entrySet()) {
            out.put(e.getKey(), e.getValue() / 1_000_000.0);
        }
        return out;
    }
}
