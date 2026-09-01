-- Papel persistido do membro no grupo familiar (OWNER | MEMBER).
-- OWNER administra o grupo; MEMBER apenas participa/consulta.
ALTER TABLE grupo_familiar_membros
    ADD COLUMN IF NOT EXISTS papel varchar(16) NOT NULL DEFAULT 'MEMBER';

-- Backfill: o criador do grupo é o OWNER.
UPDATE grupo_familiar_membros m
SET papel = 'OWNER'
FROM grupos_familiares g
WHERE m.grupo_familiar_id = g.id
  AND m.usuario_id = g.criador_usuario_id;
