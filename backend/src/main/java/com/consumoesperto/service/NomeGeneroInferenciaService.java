package com.consumoesperto.service;

import com.consumoesperto.model.Usuario;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Inferência leve de gênero a partir do primeiro nome (PT-BR comum).
 * Usado quando o Google não expõe gênero (privacidade ou escopo).
 */
@Service
public class NomeGeneroInferenciaService {

    private static final Set<String> FEMININO = Set.of(
        "ana", "andreia", "adriana", "alice", "aline", "amanda", "beatriz", "bia", "bianca", "bruna",
        "camila", "carla", "carolina", "cassia", "cecilia", "celia", "clara", "claudia",
        "daniela", "debora", "elaine", "elisa", "ellen", "emanuela", "fabiana", "fernanda", "franciele",
        "gabriela", "giselle", "graziele", "helena", "isabel", "isabela", "isabella", "jessica",
        "joana", "juliana", "juliane", "karina", "kelly", "larissa", "leticia", "lia", "lilian",
        "lorena", "luana", "luciana", "luiza", "magali", "manuela", "marcela", "marcia", "mariana",
        "marilia", "marina", "melissa", "michelle", "monica", "natalia", "naomi", "natalie",
        "pamela", "patricia", "paula", "priscila", "rafaela", "raquel", "regina", "renata", "rita",
        "roberta", "rosa", "samanta", "sandra", "sara", "silvana", "simone", "sofia", "suelen",
        "susan", "suzana", "tatiane", "tereza", "thais", "valeria", "vanessa", "vera", "viviane",
        "yasmim", "yasmin"
    );

    private static final Set<String> MASCULINO = Set.of(
        "andre", "alexandre", "antonio", "augusto", "bruno", "caio", "carlos", "danilo",
        "diego", "douglas", "eduardo", "felipe", "fernando", "filipe", "flavio", "gabriel",
        "gerson", "gilberto", "giovanni", "gustavo", "guilherme", "henrique", "igor", "joao",
        "jorge", "jose", "julio", "leonardo", "lucas", "luis", "luiz", "marcelo", "marcos",
        "mateus", "matheus", "mauricio", "michel", "miguel", "murilo", "nelson", "nicolas",
        "osmar", "otto", "patrick", "paulo", "pedro", "rafael", "raphael", "renan", "renato",
        "ricardo", "roberto", "rodrigo", "rogerio", "samuel", "sergio", "silvio", "thiago",
        "tiago", "ubirajara", "victor", "vinicius", "wagner", "wellington", "william"
    );

    /**
     * @param primeiroNome primeiro nome já isolado ou {@code null}
     */
    public Optional<Usuario.GeneroUsuario> inferirPrimeiroNome(String primeiroNome) {
        if (primeiroNome == null || primeiroNome.isBlank()) {
            return Optional.empty();
        }
        String n = normalizar(primeiroNome.trim().split("\\s+")[0]);
        if (n.isEmpty()) {
            return Optional.empty();
        }
        if (FEMININO.contains(n)) {
            return Optional.of(Usuario.GeneroUsuario.FEMALE);
        }
        if (MASCULINO.contains(n)) {
            return Optional.of(Usuario.GeneroUsuario.MALE);
        }
        return Optional.empty();
    }

    private static String normalizar(String s) {
        String decomposed = Normalizer.normalize(s, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "");
        return decomposed.toLowerCase(Locale.ROOT);
    }
}
