-- Papel de autorização persistido do usuário (USER | ADMIN).
-- A promoção a ADMIN é operação administrativa explícita no banco (ou via ops),
-- nunca inferida por e-mail, primeiro registro ou payload do cliente.
ALTER TABLE usuarios
    ADD COLUMN IF NOT EXISTS role varchar(20) NOT NULL DEFAULT 'USER';
