package com.consumoesperto.autonomy;

/**
 * Canal de entrega JARVIS. O domínio não chama EvolutionApiService.
 */
public interface JarvisNotificationChannel {

    String name();

    boolean deliver(Long usuarioId, String title, String message, JarvisNotificationPriority priority);

    default boolean deliver(
        Long usuarioId,
        String title,
        String message,
        JarvisNotificationPriority priority,
        Long financialEventId,
        Long resourceId
    ) {
        return deliver(usuarioId, title, message, priority);
    }

    default void retryPending() {
        // no-op
    }
}
