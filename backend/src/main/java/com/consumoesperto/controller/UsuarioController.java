package com.consumoesperto.controller;

import com.consumoesperto.dto.UsuarioDTO;
import com.consumoesperto.service.UsuarioService;
import com.consumoesperto.security.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Controller para operações relacionadas a usuários
 * 
 * Este controller gerencia operações de usuários como:
 * - Buscar perfil do usuário autenticado
 * - Atualizar dados do usuário
 * - Operações administrativas (para administradores)
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
@Slf4j
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final SecurityService securityService;

    /**
     * Busca o perfil do usuário autenticado
     * 
     * @return Dados do usuário autenticado
     */
    @GetMapping("/perfil")
    public ResponseEntity<UsuarioDTO> getPerfil() {
        try {
            log.info("🔍 Buscando perfil do usuário autenticado");
            
            Optional<com.consumoesperto.model.Usuario> usuarioOpt = securityService.getCurrentUser();
            if (!usuarioOpt.isPresent()) {
                log.warn("❌ Usuário não autenticado");
                return ResponseEntity.status(401).build();
            }

            com.consumoesperto.model.Usuario usuario = usuarioOpt.get();
            log.info("✅ Perfil encontrado para usuário: {}", usuario.getEmail());
            
            // Converte para DTO
            UsuarioDTO usuarioDTO = new UsuarioDTO();
            usuarioDTO.setId(usuario.getId());
            usuarioDTO.setUsername(usuario.getUsername());
            usuarioDTO.setEmail(usuario.getEmail());
            usuarioDTO.setNome(usuario.getNome());
            usuarioDTO.setFotoUrl(usuario.getFotoUrl()); // Adiciona foto
            usuarioDTO.setDataCriacao(usuario.getDataCriacao());
            
            return ResponseEntity.ok(usuarioDTO);
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar perfil: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Atualiza dados do usuário autenticado
     * 
     * @param usuarioDTO Dados atualizados do usuário
     * @return Usuário atualizado
     */
    @PutMapping("/perfil")
    public ResponseEntity<UsuarioDTO> atualizarPerfil(@RequestBody UsuarioDTO usuarioDTO) {
        try {
            log.info("🔄 Atualizando perfil do usuário autenticado");
            
            Optional<com.consumoesperto.model.Usuario> usuarioOpt = securityService.getCurrentUser();
            if (!usuarioOpt.isPresent()) {
                log.warn("❌ Usuário não autenticado");
                return ResponseEntity.status(401).build();
            }

            // Atualiza apenas campos permitidos
            com.consumoesperto.model.Usuario usuario = usuarioOpt.get();
            if (usuarioDTO.getNome() != null) {
                usuario.setNome(usuarioDTO.getNome());
            }
            
            // Salva no banco
            com.consumoesperto.model.Usuario usuarioAtualizado = usuarioService.findById(usuario.getId());
            usuarioAtualizado.setNome(usuario.getNome());
            
            log.info("✅ Perfil atualizado para usuário: {}", usuario.getEmail());
            
            // Converte para DTO
            UsuarioDTO responseDTO = new UsuarioDTO();
            responseDTO.setId(usuarioAtualizado.getId());
            responseDTO.setUsername(usuarioAtualizado.getUsername());
            responseDTO.setEmail(usuarioAtualizado.getEmail());
            responseDTO.setNome(usuarioAtualizado.getNome());
            responseDTO.setDataCriacao(usuarioAtualizado.getDataCriacao());
            
            return ResponseEntity.ok(responseDTO);
            
        } catch (Exception e) {
            log.error("❌ Erro ao atualizar perfil: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
