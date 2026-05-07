package com.consumoesperto.dto;

import com.consumoesperto.model.Usuario;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class PreferenciaTratamentoRequest {

    @NotNull(message = "preferenciaTratamento é obrigatório")
    private Usuario.PreferenciaTratamentoJarvis preferenciaTratamento;
}
