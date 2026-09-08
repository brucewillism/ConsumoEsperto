package com.consumoesperto.edith;

import com.consumoesperto.config.EdithProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EdithResilienceTest {

    @Test
    void circuitOpenFalhaImediatoEFechaNaRecuperacao() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        CircuitBreaker cb = registry.circuitBreaker(EdithResilience.INSTANCE);
        EdithProperties props = new EdithProperties();
        props.setCircuitBreakerEnabled(true);
        props.setRetryEnabled(false);

        @SuppressWarnings("unchecked")
        ObjectProvider<CircuitBreakerRegistry> cbProv = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<io.github.resilience4j.retry.RetryRegistry> retryProv = mock(ObjectProvider.class);
        when(cbProv.getIfAvailable()).thenReturn(registry);
        when(retryProv.getIfAvailable()).thenReturn(null);

        EdithResilience resilience = new EdithResilience(props, cbProv, retryProv);
        cb.transitionToOpenState();
        assertEquals("OPEN", resilience.circuitState());
        assertThrows(CallNotPermittedException.class, () -> resilience.execute(() -> "ok"));

        cb.transitionToClosedState();
        assertEquals("CLOSED", resilience.circuitState());
        assertEquals("ok", resilience.execute(() -> "ok"));
    }
}
