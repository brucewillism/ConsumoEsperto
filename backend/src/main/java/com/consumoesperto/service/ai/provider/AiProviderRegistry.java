package com.consumoesperto.service.ai.provider;

import com.consumoesperto.service.AiProviderType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registo extensível de adaptadores — novos provedores registam-se aqui sem mudar {@code AiRouterService}.
 */
@Component
public class AiProviderRegistry {

    private final Map<String, AiProviderAdapter> byId = new LinkedHashMap<>();

    public AiProviderRegistry(List<AiProviderAdapter> adapters) {
        if (adapters != null) {
            for (AiProviderAdapter a : adapters) {
                if (a != null && a.providerId() != null) {
                    byId.put(a.providerId().toUpperCase(), a);
                }
            }
        }
    }

    public Optional<AiProviderAdapter> find(String providerId) {
        if (providerId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(providerId.trim().toUpperCase()));
    }

    public Optional<AiProviderAdapter> find(AiProviderType type) {
        if (type == null) {
            return Optional.empty();
        }
        return find(type.name());
    }

    public List<AiProviderAdapter> allEnabled() {
        List<AiProviderAdapter> out = new ArrayList<>();
        for (AiProviderAdapter a : byId.values()) {
            if (a.isEnabled()) {
                out.add(a);
            }
        }
        return Collections.unmodifiableList(out);
    }

    public List<String> registeredIds() {
        return List.copyOf(byId.keySet());
    }
}
