package com.consumoesperto.service;

import com.consumoesperto.dto.ConviteGrupoFamiliarRequest;
import com.consumoesperto.dto.GrupoFamiliarDTO;
import com.consumoesperto.dto.GrupoFamiliarMembroDTO;
import com.consumoesperto.dto.GrupoFamiliarRequest;
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
        Usuario convidador = usuarioRepository.findById(usuarioId).orElseThrow();
        String email = request != null && request.getEmail() != null ? request.getEmail().trim() : "";
        String whatsapp = request != null && request.getWhatsapp() != null ? request.getWhatsapp().trim() : "";
        if (email.isBlank() && whatsapp.isBlank()) {
            throw new IllegalArgumentException("Informe e-mail ou WhatsApp do convidado.");
        }

        Usuario convidado = resolveUsuario(email, whatsapp).orElse(null);
        if (convidado != null && membroRepository.findByGrupoFamiliarIdAndUsuarioId(grupo.getId(), convidado.getId()).isPresent()) {
            throw new IllegalArgumentException("Usuário já faz parte deste grupo.");
        }

        GrupoFamiliarMembro convite = new GrupoFamiliarMembro();
        convite.setGrupoFamiliar(grupo);
        convite.setUsuario(convidado);
        convite.setConvidadoPor(convidador);
        convite.setConviteEmail(email.isBlank() ? null : email);
        convite.setConviteWhatsapp(whatsapp.isBlank() ? null : whatsapp);
        convite.setTokenConvite(UUID.randomUUID().toString());
        convite.setStatus(GrupoFamiliarMembro.Status.PENDENTE);
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
            .map(m -> toMembroDto(m, usuarioId))
            .collect(Collectors.toList());
    }

    @Transactional
    public GrupoFamiliarDTO responderConvite(Long usuarioId, Long membroId, boolean aceitar) {
        GrupoFamiliarMembro convite = membroRepository.findById(membroId)
            .orElseThrow(() -> new IllegalArgumentException("Convite não encontrado"));
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();
        if (!identidadeConviteCombina(convite, usuario)) {
            throw new IllegalArgumentException("Convite não pertence ao usuário.");
        }
        convite.setUsuario(usuario);
        convite.setStatus(aceitar ? GrupoFamiliarMembro.Status.ACEITO : GrupoFamiliarMembro.Status.RECUSADO);
        convite.setDataResposta(LocalDateTime.now());
        membroRepository.save(convite);
        return toDto(convite.getGrupoFamiliar(), usuarioId);
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

    private Optional<Usuario> resolveUsuario(String email, String whatsapp) {
        if (email != null && !email.isBlank()) {
            Optional<Usuario> byEmail = usuarioRepository.findByEmail(email);
            if (byEmail.isPresent()) {
                return byEmail;
            }
        }
        if (whatsapp != null && !whatsapp.isBlank()) {
            return usuarioRepository.findByWhatsappNumero(whatsapp);
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
        dto.setEu(u != null && u.getId().equals(usuarioId));
        return dto;
    }
}
