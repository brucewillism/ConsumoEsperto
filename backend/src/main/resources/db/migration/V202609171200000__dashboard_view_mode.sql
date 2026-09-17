-- Visão persistida do dashboard (MONTHLY | GENERAL). Sem isto a UI usa só localStorage.
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS ultima_visao_dashboard VARCHAR(16);
