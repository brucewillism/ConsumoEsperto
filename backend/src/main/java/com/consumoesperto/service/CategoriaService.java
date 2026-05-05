package com.consumoesperto.service;

import com.consumoesperto.dto.CategoriaDTO;
import com.consumoesperto.model.Categoria;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.CategoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.consumoesperto.util.ApelidoNormalizador;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final UsuarioService usuarioService;

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
     * Categorias ativas do usuário cujo nome coincide com o identificador (normalizado).
     */
    public List<Categoria> encontrarAtivasPorApelidoNormalizado(Long usuarioId, String apelido) {
        List<Categoria> ativas = categoriaRepository.findByUsuarioIdOrderByNome(usuarioId).stream()
            .filter(c -> c.getAtivo() == null || Boolean.TRUE.equals(c.getAtivo()))
            .collect(Collectors.toList());
        return ApelidoNormalizador.filtrarPorNomeNormalizado(ativas, Categoria::getNome, apelido);
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
