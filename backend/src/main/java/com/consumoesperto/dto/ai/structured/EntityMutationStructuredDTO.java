package com.consumoesperto.dto.ai.structured;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EntityMutationStructuredDTO {

    @NotBlank
    @Pattern(regexp = "UPDATE_ENTITY_CONFIG|UPDATE_ACCOUNT_CONFIG|MANAGE_ENTITY|CONFIRM_FISCAL_PROVISION")
    private String action;

    private String manageOperation;
    private String manageTarget;
    private String targetEntity;
    private String identifier;
    private String searchPhrase;
    private String cardName;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double confianca;
}
