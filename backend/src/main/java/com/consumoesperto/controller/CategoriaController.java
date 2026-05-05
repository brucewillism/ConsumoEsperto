package com.consumoesperto.controller;

import com.consumoesperto.dto.CategoriaDTO;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.CategoriaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/categorias")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = {"http://localhost:4200", "https://*.ngrok-free.app", "https://*.ngrok.io"})
public class CategoriaController {

    private final CategoriaService categoriaService;

    @GetMapping
    public ResponseEntity<List<CategoriaDTO>> listar(@AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(categoriaService.listarPorUsuario(currentUser.getId()));
    }

    @PostMapping
    public ResponseEntity<CategoriaDTO> criar(@AuthenticationPrincipal UserPrincipal currentUser,
                                              @Valid @RequestBody CategoriaDTO dto) {
        return ResponseEntity.ok(categoriaService.criar(currentUser.getId(), dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoriaDTO> atualizar(@AuthenticationPrincipal UserPrincipal currentUser,
                                                  @PathVariable Long id,
                                                  @Valid @RequestBody CategoriaDTO dto) {
        return ResponseEntity.ok(categoriaService.atualizar(currentUser.getId(), id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@AuthenticationPrincipal UserPrincipal currentUser, @PathVariable Long id) {
        categoriaService.deletar(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
