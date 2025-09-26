package com.consumoesperto.mapper;

import com.consumoesperto.dto.UsuarioDTO;
import com.consumoesperto.model.Usuario;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

/**
 * Mapper para conversão entre Usuario e UsuarioDTO usando MapStruct
 */
@Mapper(componentModel = "spring")
public interface UsuarioMapper {

    /**
     * Converte Usuario para UsuarioDTO
     */
    @Mapping(target = "password", ignore = true) // Não mapeia a senha por segurança
    @Mapping(target = "dataCriacao", source = "dataCriacao")
    @Mapping(target = "ultimoAcesso", source = "ultimoAcesso")
    UsuarioDTO toDTO(Usuario usuario);

    /**
     * Converte UsuarioDTO para Usuario
     */
    @Mapping(target = "id", ignore = true) // ID é gerado automaticamente
    @Mapping(target = "dataCriacao", ignore = true) // Data de criação é automática
    @Mapping(target = "ultimoAcesso", ignore = true) // Data de último acesso é automática
    @Mapping(target = "googleId", ignore = true) // Google ID é gerenciado separadamente
    @Mapping(target = "transacoes", ignore = true) // Relacionamentos são gerenciados separadamente
    @Mapping(target = "categorias", ignore = true)
    @Mapping(target = "cartoesCredito", ignore = true)
    @Mapping(target = "bankApiConfigs", ignore = true)
    @Mapping(target = "comprasParceladas", ignore = true)
    Usuario toEntity(UsuarioDTO usuarioDTO);

    /**
     * Atualiza uma entidade Usuario com dados do DTO
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "dataCriacao", ignore = true)
    @Mapping(target = "ultimoAcesso", ignore = true)
    @Mapping(target = "googleId", ignore = true)
    @Mapping(target = "transacoes", ignore = true)
    @Mapping(target = "categorias", ignore = true)
    @Mapping(target = "cartoesCredito", ignore = true)
    @Mapping(target = "bankApiConfigs", ignore = true)
    @Mapping(target = "comprasParceladas", ignore = true)
    void updateEntity(@MappingTarget Usuario usuario, UsuarioDTO usuarioDTO);

    /**
     * Converte lista de Usuario para lista de UsuarioDTO
     */
    List<UsuarioDTO> toDTOList(List<Usuario> usuarios);

    /**
     * Converte lista de UsuarioDTO para lista de Usuario
     */
    List<Usuario> toEntityList(List<UsuarioDTO> usuarioDTOs);
}
