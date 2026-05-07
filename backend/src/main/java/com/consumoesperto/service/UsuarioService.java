package com.consumoesperto.service;

import com.consumoesperto.dto.UsuarioDTO;
import com.consumoesperto.exception.DataConflictException;
import com.consumoesperto.exception.ResourceNotFoundException;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Serviço responsável por gerenciar operações relacionadas a usuários
 * 
 * Este serviço implementa a lógica de negócio para criação, busca,
 * atualização e validação de usuários. Inclui validações de unicidade
 * e criptografia de senhas para segurança.
 * 
 * Funcionalidades principais:
 * - CRUD completo de usuários
 * - Validação de unicidade de username e email
 * - Criptografia de senhas
 * - Conversão entre entidades e DTOs
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Service
@RequiredArgsConstructor // Lombok: gera construtor com campos final automaticamente
@Slf4j
public class UsuarioService {

    // Repositório para operações de persistência de usuários no banco de dados
    private final UsuarioRepository usuarioRepository;

    /** Gravação explícita no PostgreSQL quando o flush JPA deixa passar inconsistências de meta-modelo/colunas herdadas. */
    private final JdbcTemplate jdbcTemplate;
    
    // Codificador de senhas para criptografia usando BCrypt ou similar
    private final PasswordEncoder passwordEncoder;

    private final JarvisProtocolService jarvisProtocolService;
    private final WhatsAppNotificationService whatsAppNotificationService;

    /**
     * Cria um novo usuário no sistema
     * 
     * Este método implementa o fluxo completo de criação de usuário:
     * 1. Valida se o username já existe no sistema
     * 2. Valida se o email já está cadastrado
     * 3. Criptografa a senha usando o PasswordEncoder
     * 4. Define a data de criação automática
     * 5. Persiste o usuário no banco de dados
     * 
     * @param usuarioDTO DTO com os dados do usuário a ser criado
     * @return UsuarioDTO com os dados do usuário criado (sem senha)
     * @throws RuntimeException se username ou email já existirem no sistema
     */
    public UsuarioDTO criarUsuario(UsuarioDTO usuarioDTO) {
        // Validação de unicidade: verifica se o username já existe
        if (usuarioRepository.existsByUsername(usuarioDTO.getUsername())) {
            throw new DataConflictException("Username já existe");
        }

        // Validação de unicidade: verifica se o email já está cadastrado
        if (usuarioRepository.existsByEmail(usuarioDTO.getEmail())) {
            throw new DataConflictException("Email já existe");
        }

        // Cria uma nova instância de usuário a partir dos dados do DTO
        Usuario usuario = new Usuario();
        usuario.setUsername(usuarioDTO.getUsername());
        
        // CRÍTICO: Criptografa a senha antes de salvar no banco
        // Nunca armazene senhas em texto plano
        usuario.setPassword(passwordEncoder.encode(usuarioDTO.getPassword()));
        
        usuario.setEmail(usuarioDTO.getEmail());
        usuario.setNome(usuarioDTO.getNome());
        
        // Define automaticamente a data de criação do usuário
        usuario.setDataCriacao(LocalDateTime.now());

        // Persiste o usuário no banco de dados
        Usuario usuarioSalvo = usuarioRepository.save(usuario);
        
        // Converte para DTO e retorna (sem informações sensíveis)
        return converterParaDTO(usuarioSalvo);
    }

    /**
     * Busca um usuário pelo seu ID único
     * 
     * Método para recuperar usuários específicos por identificador.
     * Útil para operações de atualização, exclusão e consultas específicas.
     * 
     * @param id ID único do usuário a ser buscado
     * @return UsuarioDTO com os dados do usuário encontrado
     * @throws RuntimeException se o usuário não for encontrado no sistema
     */
    public UsuarioDTO buscarPorId(Long id) {
        // Busca o usuário pelo ID ou lança exceção se não encontrar
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        return converterParaDTO(usuario);
    }

    /**
     * Busca um usuário pelo nome de usuário (username)
     * 
     * Método usado principalmente para autenticação e validação
     * de unicidade durante o processo de login.
     * 
     * @param username Nome de usuário único para busca
     * @return UsuarioDTO com os dados do usuário encontrado
     * @throws RuntimeException se o usuário não for encontrado no sistema
     */
    public UsuarioDTO buscarPorUsername(String username) {
        // Busca o usuário pelo username ou lança exceção se não encontrar
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        return converterParaDTO(usuario);
    }

    /**
     * Busca um usuário pelo endereço de email
     * 
     * Método usado para recuperação de senha, validação de unicidade
     * e operações administrativas.
     * 
     * @param email Endereço de email do usuário para busca
     * @return UsuarioDTO com os dados do usuário encontrado
     * @throws RuntimeException se o usuário não for encontrado no sistema
     */
    public UsuarioDTO buscarPorEmail(String email) {
        // Busca o usuário pelo email ou lança exceção se não encontrar
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        return converterParaDTO(usuario);
    }

    /**
     * Lista todos os usuários cadastrados no sistema
     * 
     * Método usado principalmente para operações administrativas
     * e relatórios. Retorna uma lista paginada de todos os usuários.
     * 
     * @return Lista de UsuarioDTO com todos os usuários do sistema
     */
    public List<UsuarioDTO> listarTodos() {
        // Busca todos os usuários e converte para DTOs usando Stream API
        return usuarioRepository.findAll().stream()
                .map(this::converterParaDTO)
                .collect(Collectors.toList());
    }

    /**
     * Atualiza os dados de um usuário existente
     * 
     * Este método permite atualizar informações do usuário como:
     * - Nome completo
     * - Endereço de email
     * - Senha (opcional, só se fornecida)
     * 
     * Segurança: A senha só é atualizada se uma nova for fornecida
     * 
     * @param id ID do usuário a ser atualizado
     * @param usuarioDTO DTO com os novos dados do usuário
     * @return UsuarioDTO com os dados atualizados
     * @throws RuntimeException se o usuário não for encontrado no sistema
     */
    public UsuarioDTO atualizarUsuario(Long id, UsuarioDTO usuarioDTO) {
        // Verifica se o usuário existe antes de tentar atualizar
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        // Atualiza apenas os campos permitidos (nome e email)
        usuario.setNome(usuarioDTO.getNome());
        usuario.setEmail(usuarioDTO.getEmail());
        
        // Atualiza a senha apenas se uma nova foi fornecida
        // Isso evita sobrescrever a senha com valores nulos
        if (usuarioDTO.getPassword() != null && !usuarioDTO.getPassword().isEmpty()) {
            // CRÍTICO: Sempre criptografa a nova senha
            usuario.setPassword(passwordEncoder.encode(usuarioDTO.getPassword()));
        }

        // Persiste as alterações no banco de dados
        Usuario usuarioAtualizado = usuarioRepository.save(usuario);
        return converterParaDTO(usuarioAtualizado);
    }

    /**
     * Exclui um usuário do sistema permanentemente
     * 
     * ATENÇÃO: Esta operação é irreversível e remove todos os dados
     * do usuário, incluindo transações, faturas e cartões associados.
     * 
     * @param id ID do usuário a ser excluído
     * @throws RuntimeException se o usuário não for encontrado no sistema
     */
    public void excluirUsuario(Long id) {
        // Verifica se o usuário existe antes de tentar excluir
        if (!usuarioRepository.existsById(id)) {
            throw new ResourceNotFoundException("Usuário não encontrado");
        }
        // Remove o usuário do banco de dados
        usuarioRepository.deleteById(id);
    }

    /**
     * Busca o ID de um usuário pelo username
     * 
     * @param username Username do usuário
     * @return ID do usuário
     * @throws RuntimeException se o usuário não for encontrado
     */
    public Long getUsuarioIdByUsername(String username) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        return usuario.getId();
    }

    /**
     * Busca um usuário pelo seu ID único
     * 
     * @param id ID único do usuário a ser buscado
     * @return Usuario com os dados do usuário encontrado
     * @throws RuntimeException se o usuário não for encontrado no sistema
     */
    public Usuario findById(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }

    @Transactional
    public void alterarSenha(Long usuarioId, String senhaAtual, String novaSenha) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        if (senhaAtual == null || senhaAtual.isBlank()) {
            throw new DataConflictException("Senha atual é obrigatória");
        }
        if (novaSenha == null || novaSenha.isBlank() || novaSenha.length() < 6) {
            throw new DataConflictException("Nova senha inválida. Use pelo menos 6 caracteres");
        }
        if (!passwordEncoder.matches(senhaAtual, usuario.getPassword())) {
            throw new DataConflictException("Senha atual incorreta");
        }

        usuario.setPassword(passwordEncoder.encode(novaSenha));
        usuarioRepository.save(usuario);
    }

    /**
     * Atualiza tratamento J.A.R.V.I.S., persiste labels e marca calibragem concluída.
     *
     * @param tratamentoRaw enum (SENHOR) ou rótulo (Senhora, Senhor …) ou código NENHUM
     */
    @Transactional
    public Usuario atualizarPerfilJarvis(Long usuarioId, String tratamentoRaw) {
        Usuario u = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        Usuario.PreferenciaTratamentoJarvis escolha = parseTratamentoTexto(tratamentoRaw);
        boolean eraIncompleto = !Boolean.TRUE.equals(u.getJarvisConfigurado());
        aplicarSincPreferenciaJarvis(u, escolha);
        usuarioRepository.saveAndFlush(u);
        persistirPreferenciaJarvisJdbc(usuarioId, u);
        Usuario salvo = u;
        if (eraIncompleto && Boolean.TRUE.equals(salvo.getJarvisConfigurado())) {
            String voc = jarvisProtocolService.montarVocativoCompleto(salvo);
            whatsAppNotificationService.enviarParaUsuario(usuarioId,
                jarvisProtocolService.mensagemProtocolosTratamentoEstabilizados(voc));
        }
        return salvo;
    }

    /** Aceita valores do enum, nomes PT ou apenas NENHUM. */
    public Usuario.PreferenciaTratamentoJarvis parseTratamentoTexto(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("tratamento inválido");
        }
        String s = raw.trim();
        try {
            return Usuario.PreferenciaTratamentoJarvis.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            // continua por rótulos em português
        }
        String lc = s.toLowerCase(Locale.ROOT);
        switch (lc) {
            case "senhor":
                return Usuario.PreferenciaTratamentoJarvis.SENHOR;
            case "senhora":
                return Usuario.PreferenciaTratamentoJarvis.SENHORA;
            case "doutor":
                return Usuario.PreferenciaTratamentoJarvis.DOUTOR;
            case "doutora":
                return Usuario.PreferenciaTratamentoJarvis.DOUTORA;
            case "nenhum":
            case "apenas meu nome":
            case "apenas nome":
                return Usuario.PreferenciaTratamentoJarvis.NENHUM;
            default:
                throw new IllegalArgumentException("Tratamento não reconhecido: " + raw);
        }
    }

    private static String tituloCurtoDePreferencia(Usuario.PreferenciaTratamentoJarvis p) {
        if (p == null) {
            return "";
        }
        switch (p) {
            case SENHOR:
                return "Senhor";
            case SENHORA:
                return "Senhora";
            case DOUTOR:
                return "Doutor";
            case DOUTORA:
                return "Doutora";
            case NENHUM:
            case AUTOMATICO:
            default:
                return "";
        }
    }

    private void aplicarSincPreferenciaJarvis(Usuario u, Usuario.PreferenciaTratamentoJarvis escolha) {
        Usuario.PreferenciaTratamentoJarvis e = escolha != null
            ? escolha
            : Usuario.PreferenciaTratamentoJarvis.AUTOMATICO;
        u.setPreferenciaTratamentoJarvis(e);
        if (e == Usuario.PreferenciaTratamentoJarvis.AUTOMATICO) {
            u.setGeneroConfirmado(false);
            u.setJarvisConfigurado(false);
            u.setTratamento(null);
        } else {
            u.setGeneroConfirmado(true);
            u.setJarvisConfigurado(true);
            u.setTratamento(tituloCurtoDePreferencia(e));
        }
    }

    /**
     * Atualiza apenas as colunas do protocolo J.A.R.V.I.S.; falha alto se não houver linha para o {@code id}
     * (ajuda a detectar uso de outra base/schema ou erro de segurança).
     */
    private void persistirPreferenciaJarvisJdbc(Long usuarioId, Usuario estado) {
        Usuario.PreferenciaTratamentoJarvis pref = estado.getPreferenciaTratamentoJarvis() != null
            ? estado.getPreferenciaTratamentoJarvis()
            : Usuario.PreferenciaTratamentoJarvis.AUTOMATICO;
        int rows = jdbcTemplate.update(
            "UPDATE usuarios SET preferencia_tratamento_jarvis = ?, tratamento = ?, jarvis_configurado = ?, genero_confirmado = ? WHERE id = ?",
            pref.name(),
            estado.getTratamento(),
            Boolean.TRUE.equals(estado.getJarvisConfigurado()),
            Boolean.TRUE.equals(estado.getGeneroConfirmado()),
            usuarioId);
        if (rows != 1) {
            log.error(
                "Persistência J.A.R.V.I.S.: UPDATE em usuarios afetou {} linha(s) (esperado 1) para id={}. Confira DATABASE_URL/schema e se a tabela se chama 'usuarios'.",
                rows,
                usuarioId);
            throw new IllegalStateException(
                "Não foi possível gravar o tratamento na tabela usuarios: "
                    + rows + " linha(s) afetadas (id " + usuarioId + "). Confira DATABASE_URL/schema.");
        }
    }

    /**
     * Preferência manual de tratamento J.A.R.V.I.S.; bloqueia sobrescrita OAuth quando não {@link Usuario.PreferenciaTratamentoJarvis#AUTOMATICO}.
     */
    @Transactional
    public Usuario atualizarPreferenciaTratamentoJarvis(Long usuarioId, Usuario.PreferenciaTratamentoJarvis pref) {
        Usuario u = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        boolean eraIncompleto = !Boolean.TRUE.equals(u.getJarvisConfigurado());
        Usuario.PreferenciaTratamentoJarvis escolha = pref != null ? pref : Usuario.PreferenciaTratamentoJarvis.AUTOMATICO;
        aplicarSincPreferenciaJarvis(u, escolha);
        usuarioRepository.saveAndFlush(u);
        persistirPreferenciaJarvisJdbc(usuarioId, u);
        Usuario salvo = u;
        boolean agoraConfigurado = Boolean.TRUE.equals(salvo.getJarvisConfigurado());
        if (eraIncompleto && agoraConfigurado && escolha != Usuario.PreferenciaTratamentoJarvis.AUTOMATICO) {
            String voc = jarvisProtocolService.montarVocativoCompleto(salvo);
            whatsAppNotificationService.enviarParaUsuario(usuarioId,
                jarvisProtocolService.mensagemProtocolosTratamentoEstabilizados(voc));
        }
        return salvo;
    }

    /**
     * Atualiza apenas o nome no perfil.
     */
    @Transactional
    public Usuario atualizarNomePerfil(Long usuarioId, String novoNome) {
        Usuario u = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        if (novoNome != null && !novoNome.isBlank()) {
            u.setNome(novoNome.trim());
        }
        return usuarioRepository.save(u);
    }

    /**
     * Converte uma entidade Usuario para UsuarioDTO
     * 
     * Este método é responsável por:
     * - Mapear dados da entidade para o DTO
     * - Remover informações sensíveis (como senha)
     * - Garantir que dados privados não sejam expostos
     * 
     * @param usuario Entidade Usuario a ser convertida
     * @return UsuarioDTO sem informações sensíveis
     */
    private UsuarioDTO converterParaDTO(Usuario usuario) {
        UsuarioDTO dto = new UsuarioDTO();
        dto.setId(usuario.getId());
        dto.setUsername(usuario.getUsername());
        dto.setEmail(usuario.getEmail());
        dto.setNome(usuario.getNome());
        dto.setDataCriacao(usuario.getDataCriacao());
        
        // SEGURANÇA: Nunca inclui a senha no DTO
        // Mesmo criptografada, a senha não deve ser retornada
        return dto;
    }
}
