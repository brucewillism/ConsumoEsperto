package com.consumoesperto.security;

import com.consumoesperto.eco.EcoException;
import com.consumoesperto.exception.ResourceNotFoundException;

import java.util.Optional;

/**
 * Ownership na camada de Service: recurso de outro usuário é {@code SCOPE_DENIED},
 * não 404 genérico que esconde a distinção pedida pelo contrato.
 */
public final class OwnershipChecks {

    private OwnershipChecks() {
    }

    public static <T> T requireOwned(Optional<T> owned, boolean exists, String resource) {
        if (owned.isPresent()) {
            return owned.get();
        }
        if (exists) {
            throw EcoException.scopeDenied(resource + " de outro usuário");
        }
        throw new ResourceNotFoundException(resource + " não encontrado");
    }
}
