package com.consumoesperto.mapper;

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
    @Mapping(target = "senha", ignore = true) // Não mapeia a senha por segurança
    @Mapping(target = "dataCriacao", source = "dataCriacao")
    @Mapping(target = "dataAtualizacao", source = "dataAtualizacao")
    UsuarioDTO toDTO(Usuario usuario);

    /**
     * Converte UsuarioDTO para Usuario
     */
    @Mapping(target = "id", ignore = true) // ID é gerado automaticamente
    @Mapping(target = "dataCriacao", ignore = true) // Data de criação é automática
    @Mapping(target = "dataAtualizacao", ignore = true) // Data de atualização é automática
    Usuario toEntity(UsuarioDTO usuarioDTO);

    /**
     * Atualiza uma entidade Usuario com dados do DTO
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "dataCriacao", ignore = true)
    @Mapping(target = "dataAtualizacao", ignore = true)
    void updateEntity(@MappingTarget Usuario usuario, UsuarioDTO usuarioDTO);

    /**
     * Converte lista de Usuario para lista de UsuarioDTO
     */
    List<UsuarioDTO> toDTOList(List<Usuario> usuarios);

    /**
     * Converte lista de UsuarioDTO para lista de Usuario
     */
    List<Usuario> toEntityList(List<UsuarioDTO> usuarioDTOs);

    // Classes de exemplo (seriam substituídas pelas classes reais)
    class Usuario {
        private Long id;
        private String nome;
        private String email;
        private String senha;
        private String dataCriacao;
        private String dataAtualizacao;
        private Boolean ativo;

        // Getters e Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getNome() { return nome; }
        public void setNome(String nome) { this.nome = nome; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getSenha() { return senha; }
        public void setSenha(String senha) { this.senha = senha; }
        public String getDataCriacao() { return dataCriacao; }
        public void setDataCriacao(String dataCriacao) { this.dataCriacao = dataCriacao; }
        public String getDataAtualizacao() { return dataAtualizacao; }
        public void setDataAtualizacao(String dataAtualizacao) { this.dataAtualizacao = dataAtualizacao; }
        public Boolean getAtivo() { return ativo; }
        public void setAtivo(Boolean ativo) { this.ativo = ativo; }
    }

    class UsuarioDTO {
        private String nome;
        private String email;
        private String senha;
        private String dataCriacao;
        private String dataAtualizacao;
        private Boolean ativo;

        // Getters e Setters
        public String getNome() { return nome; }
        public void setNome(String nome) { this.nome = nome; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getSenha() { return senha; }
        public void setSenha(String senha) { this.senha = senha; }
        public String getDataCriacao() { return dataCriacao; }
        public void setDataCriacao(String dataCriacao) { this.dataCriacao = dataCriacao; }
        public String getDataAtualizacao() { return dataAtualizacao; }
        public void setDataAtualizacao(String dataAtualizacao) { this.dataAtualizacao = dataAtualizacao; }
        public Boolean getAtivo() { return ativo; }
        public void setAtivo(Boolean ativo) { this.ativo = ativo; }
    }
}
