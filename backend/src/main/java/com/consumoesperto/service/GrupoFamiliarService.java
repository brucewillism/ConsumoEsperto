package com.consumoesperto.service;

import com.consumoesperto.dto.ConviteGrupoFamiliarRequest;
import com.consumoesperto.dto.GrupoFamiliarDTO;
import com.consumoesperto.dto.GrupoFamiliarMembroDTO;
import com.consumoesperto.dto.GrupoFamiliarRequest;
import com.consumoesperto.exception.AuthorizationException;
import com.consumoesperto.model.GrupoFamiliar;
import com.consumoesperto.model.GrupoFamiliarMembro;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.GrupoFamiliarMembroRepository;
import com.consumoesperto.repository.GrupoFamiliarRepository;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GrupoFamiliarService {

    private final GrupoFamiliarRepository grupoRepository;
    private final GrupoFamiliarMembroRepository membroRepository;
    private final UsuarioRepository usuarioRepository;
    private final WhatsAppNotificationService whatsAppNotificationService;
    private final WhatsAppUserMappingService whatsAppUserMappingService;

    /** Prazo de validade de um convite pendente. */
    static final int CONVITE_VALIDADE_DIAS = 7;

    @Transactional
    public GrupoFamiliarDTO criar(Long usuarioId, GrupoFamiliarRequest request) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));
        GrupoFamiliar grupo = new GrupoFamiliar();
        grupo.setNome(request != null && request.getNome() != null && !request.getNome().isBlank()
            ? request.getNome().trim()
            : "Família de " + usuario.getNome());
        grupo.setCriador(usuario);
        grupo = grupoRepository.save(grupo);

        GrupoFamiliarMembro membro = new GrupoFamiliarMembro();
        membro.setGrupoFamiliar(grupo);
        membro.setUsuario(usuario);
        membro.setConvidadoPor(usuario);
        membro.setStatus(GrupoFamiliarMembro.Status.ACEITO);
        membro.setPapel(GrupoFamiliarMembro.Papel.OWNER);
        membro.setTokenConvite(UUID.randomUUID().toString());
        membro.setDataResposta(LocalDateTime.now());
        membroRepository.save(membro);
        return toDto(grupo, usuarioId);
    }

    @Transactional(readOnly = true)
    public Optional<GrupoFamiliarDTO> meuGrupo(Long usuarioId) {
        return grupoAceitoDoUsuario(usuarioId).map(g -> toDto(g, usuarioId));
    }

    @Transactional
    public GrupoFamiliarDTO convidar(Long usuarioId, ConviteGrupoFamiliarRequest request) {
        GrupoFamiliar grupo = grupoAceitoDoUsuario(usuarioId)
            .orElseThrow(() -> new IllegalArgumentException("Crie um grupo antes de convidar."));
        exigirOwner(grupo.getId(), usuarioId);
        Usuario convidador = usuarioRepository.findById(usuarioId).orElseThrow();
        String email = request != null && request.getEmail() != null ? request.getEmail().trim() : "";
        String whatsapp = request != null && request.getWhatsapp() != null ? request.getWhatsapp().trim() : "";
        if (email.isBlank() && whatsapp.isBlank()) {
            throw new IllegalArgumentException("Informe e-mail ou WhatsApp do convidado.");
        }
        if (!whatsapp.isBlank()) {
            try {
                whatsapp = whatsAppUserMappingService.normalize(whatsapp);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(
                    "Número de WhatsApp inválido. Use DDD + número, ex.: (11) 99999-9999.");
            }
        }

        Usuario convidado = resolveUsuario(email, whatsapp).orElse(null);
        if (convidado != null) {
            GrupoFamiliarMembro existente = membroRepository
                .findByGrupoFamiliarIdAndUsuarioId(grupo.getId(), convidado.getId())
                .orElse(null);
            if (existente != null
                && (existente.getStatus() == GrupoFamiliarMembro.Status.ACEITO
                    || existente.getStatus() == GrupoFamiliarMembro.Status.PENDENTE)) {
                throw new IllegalArgumentException("Usuário já faz parte deste grupo ou tem convite ativo.");
            }
        }
        // Impede convite duplicado ativo para a mesma identidade (e-mail/WhatsApp)
        final String emailAlvo = email;
        final String whatsappAlvo = whatsapp;
        boolean duplicadoAtivo = membroRepository.findByGrupoFamiliarIdFetchUsuario(grupo.getId()).stream()
            .filter(m -> m.getStatus() == GrupoFamiliarMembro.Status.PENDENTE && !conviteExpirado(m))
            .anyMatch(m ->
                (!emailAlvo.isBlank() && emailAlvo.equalsIgnoreCase(m.getConviteEmail()))
                    || (!whatsappAlvo.isBlank() && whatsappAlvo.equals(m.getConviteWhatsapp())));
        if (duplicadoAtivo) {
            throw new IllegalArgumentException("Já existe um convite ativo para este contato.");
        }

        GrupoFamiliarMembro convite = new GrupoFamiliarMembro();
        convite.setGrupoFamiliar(grupo);
        convite.setUsuario(convidado);
        convite.setConvidadoPor(convidador);
        convite.setConviteEmail(email.isBlank() ? null : email);
        convite.setConviteWhatsapp(whatsapp.isBlank() ? null : whatsapp);
        convite.setTokenConvite(UUID.randomUUID().toString());
        convite.setStatus(GrupoFamiliarMembro.Status.PENDENTE);
        convite.setPapel(GrupoFamiliarMembro.Papel.MEMBER);
        membroRepository.save(convite);

        if (convidado != null) {
            whatsAppNotificationService.enviarParaUsuario(convidado.getId(),
                "Você recebeu um convite para participar do grupo familiar *" + grupo.getNome()
                    + "*. Entre no app em Família para aceitar formalmente.");
        }
        return toDto(grupo, usuarioId);
    }

    @Transactional(readOnly = true)
    public List<GrupoFamiliarMembroDTO> convitesPendentes(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();
        String email = usuario.getEmail() != null ? usuario.getEmail() : "";
        String whatsapp = usuario.getWhatsappNumero() != null ? usuario.getWhatsappNumero() : "";
        return membroRepository.findPendentesParaIdentidade(email, whatsapp).stream()
            .filter(m -> !conviteExpirado(m))
            .map(m -> toMembroDto(m, usuarioId))
            .collect(Collectors.toList());
    }

    @Transactional
    public GrupoFamiliarDTO responderConvite(Long usuarioId, Long membroId, boolean aceitar) {
        GrupoFamiliarMembro convite = membroRepository.findById(membroId)
            .orElseThrow(() -> new IllegalArgumentException("Convite não encontrado"));
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();
        if (!identidadeConviteCombina(convite, usuario)) {
            // Não revelar existência do convite a terceiros
            throw new IllegalArgumentException("Convite não encontrado");
        }
        if (convite.getStatus() != GrupoFamiliarMembro.Status.PENDENTE) {
            throw new IllegalArgumentException("Este convite já foi respondido ou cancelado.");
        }
        if (conviteExpirado(convite)) {
            convite.setStatus(GrupoFamiliarMembro.Status.EXPIRADO);
            convite.setDataResposta(LocalDateTime.now());
            membroRepository.save(convite);
            throw new IllegalArgumentException("Este convite expirou. Peça um novo convite ao administrador do grupo.");
        }
        convite.setUsuario(usuario);
        convite.setStatus(aceitar ? GrupoFamiliarMembro.Status.ACEITO : GrupoFamiliarMembro.Status.RECUSADO);
        convite.setDataResposta(LocalDateTime.now());
        membroRepository.save(convite);
        return toDto(convite.getGrupoFamiliar(), usuarioId);
    }

    /** Cancela um convite pendente do grupo — somente OWNER. */
    @Transactional
    public GrupoFamiliarDTO cancelarConvite(Long usuarioId, Long membroId) {
        GrupoFamiliar grupo = grupoAceitoDoUsuario(usuarioId)
            .orElseThrow(() -> new IllegalArgumentException("Você não participa de um grupo familiar."));
        exigirOwner(grupo.getId(), usuarioId);
        GrupoFamiliarMembro convite = membroRepository.findById(membroId)
            .filter(m -> m.getGrupoFamiliar() != null && grupo.getId().equals(m.getGrupoFamiliar().getId()))
            .orElseThrow(() -> new IllegalArgumentException("Convite não encontrado"));
        if (convite.getStatus() != GrupoFamiliarMembro.Status.PENDENTE) {
            throw new IllegalArgumentException("Somente convites pendentes podem ser cancelados.");
        }
        convite.setStatus(GrupoFamiliarMembro.Status.CANCELADO);
        convite.setDataResposta(LocalDateTime.now());
        membroRepository.save(convite);
        return toDto(grupo, usuarioId);
    }

    /** Remove um membro aceito do grupo — somente OWNER; OWNER não remove a si mesmo (use sair). */
    @Transactional
    public GrupoFamiliarDTO removerMembro(Long usuarioId, Long membroId) {
        GrupoFamiliar grupo = grupoAceitoDoUsuario(usuarioId)
            .orElseThrow(() -> new IllegalArgumentException("Você não participa de um grupo familiar."));
        exigirOwner(grupo.getId(), usuarioId);
        GrupoFamiliarMembro membro = membroRepository.findById(membroId)
            .filter(m -> m.getGrupoFamiliar() != null && grupo.getId().equals(m.getGrupoFamiliar().getId()))
            .orElseThrow(() -> new IllegalArgumentException("Membro não encontrado"));
        if (membro.getStatus() != GrupoFamiliarMembro.Status.ACEITO) {
            throw new IllegalArgumentException("Somente membros ativos podem ser removidos.");
        }
        if (membro.getUsuario() != null && usuarioId.equals(membro.getUsuario().getId())) {
            throw new IllegalArgumentException("Para deixar o grupo use a opção sair.");
        }
        membro.setStatus(GrupoFamiliarMembro.Status.CANCELADO);
        membro.setDataResposta(LocalDateTime.now());
        membroRepository.save(membro);
        return toDto(grupo, usuarioId);
    }

    @Transactional(readOnly = true)
    public Optional<GrupoFamiliar> grupoAceitoDoUsuario(Long usuarioId) {
        return membroRepository.findAceitosByUsuarioId(usuarioId).stream()
            .findFirst()
            .map(GrupoFamiliarMembro::getGrupoFamiliar);
    }

    @Transactional(readOnly = true)
    public List<Usuario> membrosAceitos(Long grupoId) {
        return membroRepository.findByGrupoFamiliarIdFetchUsuario(grupoId).stream()
            .filter(m -> m.getStatus() == GrupoFamiliarMembro.Status.ACEITO && m.getUsuario() != null)
            .map(GrupoFamiliarMembro::getUsuario)
            .collect(Collectors.toList());
    }

    public void exigirMembroAceito(Long grupoId, Long usuarioId) {
        GrupoFamiliarMembro membro = membroRepository.findByGrupoFamiliarIdAndUsuarioId(grupoId, usuarioId)
            .orElseThrow(() -> new IllegalArgumentException("Usuário não pertence ao grupo."));
        if (membro.getStatus() != GrupoFamiliarMembro.Status.ACEITO) {
            throw new IllegalArgumentException("Convite ainda não foi aceito.");
        }
    }

    @Transactional
    public GrupoFamiliarDTO renomear(Long usuarioId, GrupoFamiliarRequest request) {
        GrupoFamiliar grupo = grupoAceitoDoUsuario(usuarioId)
            .orElseThrow(() -> new IllegalArgumentException("Você não participa de um grupo familiar."));
        exigirOwner(grupo.getId(), usuarioId);
        String nome = request != null && request.getNome() != null ? request.getNome().trim() : "";
        if (nome.isBlank()) {
            throw new IllegalArgumentException("Informe um nome para o grupo.");
        }
        if (nome.length() > 120) {
            throw new IllegalArgumentException("Nome do grupo deve ter no máximo 120 caracteres.");
        }
        grupo.setNome(nome);
        grupoRepository.save(grupo);
        return toDto(grupo, usuarioId);
    }

    @Transactional
    public void sair(Long usuarioId) {
        GrupoFamiliarMembro membro = membroRepository.findAceitosByUsuarioId(usuarioId).stream()
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Você não participa de um grupo familiar."));
        if (membro.getPapel() == GrupoFamiliarMembro.Papel.OWNER) {
            long outrosAtivos = membroRepository
                .findByGrupoFamiliarIdFetchUsuario(membro.getGrupoFamiliar().getId()).stream()
                .filter(m -> m.getStatus() == GrupoFamiliarMembro.Status.ACEITO)
                .filter(m -> m.getUsuario() == null || !usuarioId.equals(m.getUsuario().getId()))
                .count();
            if (outrosAtivos > 0) {
                throw new IllegalArgumentException(
                    "O administrador só pode encerrar o grupo depois de remover os demais membros.");
            }
        }
        membro.setStatus(GrupoFamiliarMembro.Status.CANCELADO);
        membro.setDataResposta(LocalDateTime.now());
        membroRepository.save(membro);
    }

    /** Lança 403 quando o usuário não é OWNER ativo do grupo. */
    private void exigirOwner(Long grupoId, Long usuarioId) {
        GrupoFamiliarMembro membro = membroRepository.findByGrupoFamiliarIdAndUsuarioId(grupoId, usuarioId)
            .orElseThrow(() -> new AuthorizationException("Apenas o administrador do grupo pode executar esta ação."));
        if (membro.getStatus() != GrupoFamiliarMembro.Status.ACEITO
            || membro.getPapel() != GrupoFamiliarMembro.Papel.OWNER) {
            throw new AuthorizationException("Apenas o administrador do grupo pode executar esta ação.");
        }
    }

    private static boolean conviteExpirado(GrupoFamiliarMembro convite) {
        return convite.getStatus() == GrupoFamiliarMembro.Status.PENDENTE
            && convite.getDataConvite() != null
            && convite.getDataConvite().isBefore(LocalDateTime.now().minusDays(CONVITE_VALIDADE_DIAS));
    }

    private Optional<Usuario> resolveUsuario(String email, String whatsapp) {
        if (email != null && !email.isBlank()) {
            Optional<Usuario> byEmail = usuarioRepository.findByEmail(email);
            if (byEmail.isPresent()) {
                return byEmail;
            }
        }
        if (whatsapp != null && !whatsapp.isBlank()) {
            return whatsAppUserMappingService.findByIncomingNumber(whatsapp);
        }
        return Optional.empty();
    }

    private boolean identidadeConviteCombina(GrupoFamiliarMembro convite, Usuario usuario) {
        if (convite.getUsuario() != null && convite.getUsuario().getId().equals(usuario.getId())) {
            return true;
        }
        String email = usuario.getEmail() != null ? usuario.getEmail() : "";
        String whats = usuario.getWhatsappNumero() != null ? usuario.getWhatsappNumero() : "";
        return (convite.getConviteEmail() != null && convite.getConviteEmail().equalsIgnoreCase(email))
            || (convite.getConviteWhatsapp() != null && convite.getConviteWhatsapp().equals(whats));
    }

    private GrupoFamiliarDTO toDto(GrupoFamiliar grupo, Long usuarioId) {
        GrupoFamiliarDTO dto = new GrupoFamiliarDTO();
        dto.setId(grupo.getId());
        dto.setNome(grupo.getNome());
        dto.setMembros(membroRepository.findByGrupoFamiliarIdFetchUsuario(grupo.getId()).stream()
            .map(m -> toMembroDto(m, usuarioId))
            .collect(Collectors.toList()));
        return dto;
    }

    private GrupoFamiliarMembroDTO toMembroDto(GrupoFamiliarMembro m, Long usuarioId) {
        GrupoFamiliarMembroDTO dto = new GrupoFamiliarMembroDTO();
        dto.setId(m.getId());
        Usuario u = m.getUsuario();
        dto.setUsuarioId(u != null ? u.getId() : null);
        dto.setNome(u != null ? u.getNome() : "Convidado");
        dto.setEmail(u != null ? u.getEmail() : m.getConviteEmail());
        dto.setWhatsapp(u != null ? u.getWhatsappNumero() : m.getConviteWhatsapp());
        dto.setStatus(m.getStatus() != null ? m.getStatus().name() : null);
        dto.setPapel(m.getPapel() != null ? m.getPapel().name() : GrupoFamiliarMembro.Papel.MEMBER.name());
        dto.setEu(u != null && u.getId().equals(usuarioId));
        return dto;
    }
}
