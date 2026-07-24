package com.consumoesperto.service.ai;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AiRouterRequestContext {

    private final Long userId;
    private final Double temperature;

    public static AiRouterRequestContext of(Long userId) {
        return AiRouterRequestContext.builder().userId(userId).build();
    }

    public static AiRouterRequestContext of(Long userId, double temperature) {
        return AiRouterRequestContext.builder().userId(userId).temperature(temperature).build();
    }
}
