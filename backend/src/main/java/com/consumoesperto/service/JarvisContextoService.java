package com.consumoesperto.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Estado conversacional leve do J.A.R.V.I.S. por utilizador (em memória).
 */
@Service
public class JarvisContextoService {

    private final Map<Long, JarvisContexto> contextos = new ConcurrentHashMap<>();

    public JarvisContexto obter(Long usuarioId) {
        if (usuarioId == null) {
            return new JarvisContexto();
        }
        return contextos.computeIfAbsent(usuarioId, id -> new JarvisContexto());
    }

    @Getter
    @Setter
    public static class JarvisContexto {
        private int ultimoIndiceSaudacao = -1;
    }
}
