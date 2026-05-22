package com.consumoesperto.security;

import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        String login = usernameOrEmail != null ? usernameOrEmail.trim() : "";
        if (login.isEmpty()) {
            throw new UsernameNotFoundException("Credencial de login vazia");
        }

        Usuario usuario = usuarioRepository.findByUsernameOrEmail(login)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

        return UserPrincipal.create(usuario);
    }

    @Transactional
    public UserDetails loadUserById(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado com id: " + id));

        return UserPrincipal.create(usuario);
    }
}
