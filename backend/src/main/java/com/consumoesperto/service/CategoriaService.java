package com.consumoesperto.service;

import com.consumoesperto.dto.CategoriaDTO;
import com.consumoesperto.dto.MatchResult;
import com.consumoesperto.model.Categoria;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.CategoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final UsuarioService usuarioService;
    private final TextMatcherService textMatcherService;

    public List<CategoriaDTO> listarPorUsuario(Long usuarioId) {
        return categoriaRepository.findByUsuarioIdOrderByNome(usuarioId).stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    @Transactional
    public CategoriaDTO criar(Long usuarioId, CategoriaDTO dto) {
        if (categoriaRepository.existsByNomeAndUsuarioId(dto.getNome(), usuarioId)) {
            throw new RuntimeException("Categoria com este nome já existe");
        }
        Usuario usuario = usuarioService.findById(usuarioId);
        Categoria categoria = new Categoria();
        categoria.setNome(dto.getNome());
        categoria.setDescricao(dto.getDescricao());
        categoria.setCor(dto.getCor());
        categoria.setIcone(dto.getIcone());
        categoria.setUsuario(usuario);
        return toDto(categoriaRepository.save(categoria));
    }

    /**
     * Categorias ativas do usuário cujo nome coincide com o identificador (normalizado + fuzzy).
     */
    @Transactional(readOnly = true)
    public Map<Long, String> mapearNomesPorId(Long usuarioId) {
        Map<Long, String> map = new LinkedHashMap<>();
        for (Categoria c : listarAtivas(usuarioId)) {
            if (c.getId() != null && c.getNome() != null) {
                map.put(c.getId(), c.getNome());
            }
        }
        return map;
    }

    public List<Categoria> encontrarAtivasPorApelidoNormalizado(Long usuarioId, String apelido) {
        if (apelido == null || apelido.isBlank()) {
            return List.of();
        }
        MatchResult match = textMatcherService.resolverEntidade(apelido, mapearNomesPorId(usuarioId));
        return categoriasDeMatch(usuarioId, match);
    }

    private List<Categoria> listarAtivas(Long usuarioId) {
        return categoriaRepository.findByUsuarioIdOrderByNome(usuarioId).stream()
            .filter(c -> c.getAtivo() == null || Boolean.TRUE.equals(c.getAtivo()))
            .collect(Collectors.toList());
    }

    private List<Categoria> categoriasDeMatch(Long usuarioId, MatchResult match) {
        if (match == null) {
            return List.of();
        }
        return switch (match.getConfianca()) {
            case EXATO, PARCIAL, FUZZY -> categoriaRepository.findById(match.getIdResolvido())
                .filter(c -> c.getUsuario() != null && usuarioId.equals(c.getUsuario().getId()))
                .map(List::of)
                .orElse(List.of());
            case AMBIGUO -> match.getOpcoes().stream()
                .map(e -> categoriaRepository.findById(e.getKey()).orElse(null))
                .filter(c -> c != null && c.getUsuario() != null && usuarioId.equals(c.getUsuario().getId()))
                .collect(Collectors.toList());
            default -> List.of();
        };
    }

    @Transactional
    public CategoriaDTO atualizar(Long usuarioId, Long categoriaId, CategoriaDTO dto) {
        Categoria categoria = categoriaRepository.findById(categoriaId)
            .orElseThrow(() -> new RuntimeException("Categoria não encontrada"));
        if (!categoria.getUsuario().getId().equals(usuarioId)) {
            throw new RuntimeException("Categoria não pertence ao usuário");
        }
        categoria.setNome(dto.getNome());
        categoria.setDescricao(dto.getDescricao());
        categoria.setCor(dto.getCor());
        categoria.setIcone(dto.getIcone());
        return toDto(categoriaRepository.save(categoria));
    }

    @Transactional
    public void deletar(Long usuarioId, Long categoriaId) {
        Categoria categoria = categoriaRepository.findById(categoriaId)
            .orElseThrow(() -> new RuntimeException("Categoria não encontrada"));
        if (!categoria.getUsuario().getId().equals(usuarioId)) {
            throw new RuntimeException("Categoria não pertence ao usuário");
        }
        categoriaRepository.delete(categoria);
    }

    private CategoriaDTO toDto(Categoria categoria) {
        CategoriaDTO dto = new CategoriaDTO();
        dto.setId(categoria.getId());
        dto.setNome(categoria.getNome());
        dto.setDescricao(categoria.getDescricao());
        dto.setCor(categoria.getCor());
        dto.setIcone(categoria.getIcone());
        dto.setUsuarioId(categoria.getUsuario() != null ? categoria.getUsuario().getId() : null);
        dto.setDataCriacao(categoria.getDataCriacao());
        return dto;
    }
}
