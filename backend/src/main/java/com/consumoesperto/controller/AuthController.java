package com.consumoesperto.controller;

import com.consumoesperto.dto.LoginDTO;
import com.consumoesperto.dto.UsuarioDTO;
import com.consumoesperto.dto.GoogleLoginDTO;
import com.consumoesperto.security.JwtTokenProvider;
import com.consumoesperto.service.UsuarioService;
import com.consumoesperto.service.GoogleOAuth2Service;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller responsável por gerenciar operações de autenticação
 * 
 * Este controller expõe endpoints para login, registro e autenticação
 * via Google OAuth2. Gerencia a autenticação de usuários e geração
 * de tokens JWT para acesso ao sistema.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@RestController
@RequestMapping("/api/auth") // Base path para todos os endpoints de autenticação
@RequiredArgsConstructor // Lombok: gera construtor com campos final
@Api(tags = "Autenticação") // Documentação Swagger
@CrossOrigin(origins = "*") // Permite CORS de qualquer origem
public class AuthController {

    // Gerenciador de autenticação do Spring Security
    private final AuthenticationManager authenticationManager;
    
    // Provedor de tokens JWT
    private final JwtTokenProvider tokenProvider;
    
    // Serviço para operações com usuários
    private final UsuarioService usuarioService;
    
    // Serviço para autenticação via Google OAuth2
    private final GoogleOAuth2Service googleOAuth2Service;

    /**
     * Endpoint para realizar login de usuário
     * 
     * Autentica as credenciais do usuário e retorna um token JWT
     * para acesso às funcionalidades protegidas do sistema.
     * 
     * @param loginDTO DTO contendo username e password
     * @return ResponseEntity com token JWT e tipo de autenticação
     */
    @PostMapping("/login")
    @ApiOperation("Realizar login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginDTO loginDTO) {
        // Cria um token de autenticação com as credenciais fornecidas
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginDTO.getUsername(),
                        loginDTO.getPassword()
                )
        );

        // Define a autenticação no contexto de segurança
        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        // Gera o token JWT para o usuário autenticado
        String jwt = tokenProvider.generateToken(authentication);

        // Prepara a resposta com o token
        Map<String, String> response = new HashMap<>();
        response.put("token", jwt);
        response.put("type", "Bearer");

        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint para registrar novo usuário
     * 
     * Cria uma nova conta de usuário no sistema com as informações
     * fornecidas. A senha é criptografada antes de ser armazenada.
     * 
     * @param usuarioDTO DTO contendo dados do novo usuário
     * @return ResponseEntity com os dados do usuário criado
     */
    @PostMapping("/registro")
    @ApiOperation("Registrar novo usuário")
    public ResponseEntity<?> registro(@Valid @RequestBody UsuarioDTO usuarioDTO) {
        // Cria o usuário através do serviço
        UsuarioDTO novoUsuario = usuarioService.criarUsuario(usuarioDTO);
        return ResponseEntity.ok(novoUsuario);
    }

    /**
     * Endpoint para autenticação via Google OAuth2
     * 
     * Permite que usuários se autentiquem usando suas contas Google.
     * Se o usuário não existir, uma nova conta é criada automaticamente.
     * 
     * @param googleLoginDTO DTO contendo o token do Google
     * @return ResponseEntity com token JWT e dados do usuário
     */
    @PostMapping("/google")
    @ApiOperation("Login com Google")
    public ResponseEntity<?> googleLogin(@Valid @RequestBody GoogleLoginDTO googleLoginDTO) {
        try {
            // Autentica o usuário via Google
            UsuarioDTO usuarioDTO = googleOAuth2Service.authenticateGoogleUser(googleLoginDTO.getIdToken());
            
            // Gera o token JWT para o usuário autenticado
            String jwt = tokenProvider.generateToken(usuarioDTO.getUsername());
            
            // Prepara a resposta com o token e dados do usuário
            Map<String, Object> response = new HashMap<>();
            response.put("token", jwt);
            response.put("type", "Bearer");
            response.put("usuario", usuarioDTO);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erro na autenticação via Google: " + e.getMessage());
        }
    }
}
