#!/usr/bin/env bash
# =============================================================================
# ConsumoEsperto — preparação de servidor Linux (Ubuntu 22.04/24.04 ou Debian 12+)
# =============================================================================
# O que instala: OpenJDK 17, Maven, Node.js 20 LTS, PostgreSQL, Nginx, Git, UFW.
# Cria bases: consumoesperto e evolution_api (mesmo utilizador PostgreSQL).
#
# Uso no servidor (recomendado):
#   scp scripts/instalar-servidor-linux.sh user@servidor:~
#   ssh user@servidor
#   chmod +x instalar-servidor-linux.sh
#   sudo ./instalar-servidor-linux.sh
#
# Palavra-passe da BD: define antes (opcional) ou o script gera uma e mostra no fim.
#   export CE_DB_PASSWORD='a_tua_senha_segura'
#   sudo -E ./instalar-servidor-linux.sh
#
# Variáveis opcionais:
#   CE_SKIP_NODE=1         — não instala Node (também desativa Evolution em código)
#   CE_SKIP_EVOLUTION=1    — não clona/compila a Evolution (só BD evolution_api)
#   CE_EVOLUTION_HOME=/opt/evolution-api
#   CE_EVOLUTION_TAG=v2.3.7   — tag do repositório oficial EvolutionAPI/evolution-api
#   CE_EVOLUTION_SERVER_URL — URL pública da API (ex.: https://evo.teudominio.com); default http://127.0.0.1:8081
#   CE_FORCE_EVOLUTION_ENV=1 — volta a escrever .env da Evolution (sobrescreve apikey local)
#   CE_SKIP_NGINX=1      — não instala Nginx
#   CE_SKIP_UFW=1        — não configura firewall
#   CE_DB_USER=consumoesperto  — nome do role PostgreSQL (default: consumoesperto)
#
# Segurança (produção): no backend usa SPRING_PROFILES_ACTIVE=prod e define JWT_SECRET,
# DATABASE_*, CORS_ALLOWED_PATTERNS, EVOLUTION_APIKEY (igual a AUTHENTICATION_API_KEY no .env da Evolution).
# =============================================================================
set -euo pipefail

CE_EVOLUTION_APIKEY_GENERATED=""

log() { printf '\n[\033[0;32m*\033[0m] %s\n' "$*"; }
warn() { printf '\n[\033[0;33m!\033[0m] %s\n' "$*" >&2; }
die() { printf '\n[\033[0;31mx\033[0m] %s\n' "$*" >&2; exit 1; }

# Clone, dependências, build, Prisma e .env da Evolution API (oficial).
install_evolution_api() {
  local EVO_HOME="${CE_EVOLUTION_HOME:-/opt/evolution-api}"
  local TAG="${CE_EVOLUTION_TAG:-v2.3.7}"
  local PUB_URL="${CE_EVOLUTION_SERVER_URL:-http://127.0.0.1:8081}"
  local ENC_PASS
  ENC_PASS="$(python3 -c "import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1], safe=''))" "$DB_PASS")"
  local URI="postgresql://${DB_USER}:${ENC_PASS}@127.0.0.1:5432/evolution_api?schema=public"

  if [[ "${CE_SKIP_EVOLUTION:-0}" == "1" ]]; then
    warn "Evolution (clone/build) ignorado (CE_SKIP_EVOLUTION=1)."
    return 0
  fi
  if ! command -v node >/dev/null 2>&1; then
    warn "Node não encontrado — não foi possível instalar a Evolution."
    return 0
  fi

  log "A preparar Evolution API em ${EVO_HOME} (tag ${TAG})..."
  if [[ -d "$EVO_HOME" && ! -d "$EVO_HOME/.git" ]]; then
    die "Já existe ${EVO_HOME} sem ser repositório git. Apaga ou define CE_EVOLUTION_HOME."
  fi
  if [[ ! -d "$EVO_HOME/.git" ]]; then
    mkdir -p "$(dirname "$EVO_HOME")"
    git clone --depth 1 --branch "$TAG" https://github.com/EvolutionAPI/evolution-api.git "$EVO_HOME" \
      || die "Clone da Evolution falhou (rede ou tag inexistente). Experimenta CE_EVOLUTION_TAG=main"
  else
    log "Repositório Evolution já existe; a atualizar para ${TAG}..."
    git -C "$EVO_HOME" fetch --tags origin 2>/dev/null || true
    git -C "$EVO_HOME" checkout "$TAG" 2>/dev/null || git -C "$EVO_HOME" checkout main 2>/dev/null || true
  fi

  local AUTH_KEY
  AUTH_KEY="$(openssl rand -hex 24)"
  if [[ -f "$EVO_HOME/.env" && "${CE_FORCE_EVOLUTION_ENV:-0}" != "1" ]]; then
    warn "Mantém-se o .env existente em ${EVO_HOME} (usa CE_FORCE_EVOLUTION_ENV=1 para regenerar)."
    if grep -q '^AUTHENTICATION_API_KEY=' "$EVO_HOME/.env" 2>/dev/null; then
      AUTH_KEY="$(grep '^AUTHENTICATION_API_KEY=' "$EVO_HOME/.env" | head -1 | cut -d= -f2-)"
    fi
  else
    log "A escrever ${EVO_HOME}/.env (AUTHENTICATION_API_KEY + PostgreSQL evolution_api)..."
    umask 077
    cat >"$EVO_HOME/.env" <<EVOENV
AUTHENTICATION_API_KEY=${AUTH_KEY}
DATABASE_PROVIDER=postgresql
DATABASE_CONNECTION_URI=${URI}
SERVER_URL=${PUB_URL}
CONFIG_SESSION_PHONE_CLIENT=Evolution API
CONFIG_SESSION_PHONE_NAME=Chrome
EVOENV
    chmod 600 "$EVO_HOME/.env" || true
  fi
  CE_EVOLUTION_APIKEY_GENERATED="$AUTH_KEY"

  log "A instalar dependências e compilar Evolution (npm ci, build, db:deploy)..."
  ( cd "$EVO_HOME" && npm ci && npm run build && npm run db:deploy )

  if [[ -n "${SUDO_USER:-}" ]]; then
    chown -R "$SUDO_USER:$SUDO_USER" "$EVO_HOME" || true
  fi
  log "Evolution API pronta em ${EVO_HOME}. Arranque: cd ${EVO_HOME} && npm run start:prod (porta 8081 por defeito)."
}

if [[ "$(id -u)" -ne 0 ]]; then
  die "Corre com sudo: sudo $0"
fi

if [[ -f /etc/os-release ]]; then
  # shellcheck source=/dev/null
  . /etc/os-release
else
  die "Não foi possível detectar a distribuição (/etc/os-release em falta)."
fi

case "${ID:-}" in
  ubuntu|debian) ;;
  *) die "Este script foi testado para Ubuntu/Debian. Distro: ${ID:-desconhecida}. Adapta os comandos de pacotes manualmente." ;;
esac

export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y --no-install-recommends \
  ca-certificates curl wget git gnupg lsb-release \
  apt-transport-https software-properties-common \
  unzip ufw openssl python3-minimal

log "A instalar OpenJDK 17 e Maven..."
apt-get install -y --no-install-recommends openjdk-17-jdk maven

log "A instalar PostgreSQL..."
apt-get install -y --no-install-recommends postgresql postgresql-contrib
systemctl enable --now postgresql

if [[ "${CE_SKIP_NODE:-0}" != "1" ]]; then
  log "A instalar Node.js 20.x (NodeSource)..."
  curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
  apt-get install -y --no-install-recommends nodejs
else
  warn "Node.js ignorado (CE_SKIP_NODE=1)."
fi

if [[ "${CE_SKIP_NGINX:-0}" != "1" ]]; then
  log "A instalar Nginx..."
  apt-get install -y --no-install-recommends nginx
  systemctl enable --now nginx
else
  warn "Nginx ignorado (CE_SKIP_NGINX=1)."
fi

apt-get clean
rm -rf /var/lib/apt/lists/*

DB_USER="${CE_DB_USER:-consumoesperto}"
if [[ -n "${CE_DB_PASSWORD:-}" ]]; then
  DB_PASS="$CE_DB_PASSWORD"
else
  DB_PASS="$(openssl rand -base64 24 | tr -d '/+=' | head -c 24)"
  warn "CE_DB_PASSWORD não definida — foi gerada uma palavra-passe aleatória (guarda-a!)."
fi
# Escape SQL single quotes
DB_PASS_ESC="${DB_PASS//\'/\'\'}"

log "A criar utilizador e bases PostgreSQL (${DB_USER}, consumoesperto, evolution_api)..."
sudo -u postgres psql -v ON_ERROR_STOP=1 <<EOSQL
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${DB_USER}') THEN
    CREATE ROLE ${DB_USER} LOGIN PASSWORD '${DB_PASS_ESC}';
  ELSE
    ALTER ROLE ${DB_USER} PASSWORD '${DB_PASS_ESC}';
  END IF;
END
\$\$;
SELECT 'CREATE DATABASE consumoesperto OWNER ${DB_USER}'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'consumoesperto')\gexec
SELECT 'CREATE DATABASE evolution_api OWNER ${DB_USER}'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'evolution_api')\gexec
GRANT ALL PRIVILEGES ON DATABASE consumoesperto TO ${DB_USER};
GRANT ALL PRIVILEGES ON DATABASE evolution_api TO ${DB_USER};
EOSQL

if [[ "${CE_SKIP_NODE:-0}" != "1" ]] && [[ "${CE_SKIP_EVOLUTION:-0}" != "1" ]]; then
  install_evolution_api || warn "Passo Evolution falhou; podes repetir manualmente (ver documentação no repositório)."
fi

if [[ "${CE_SKIP_UFW:-0}" != "1" ]]; then
  log "A configurar UFW (SSH, HTTP, HTTPS)..."
  ufw allow OpenSSH || true
  ufw allow 80/tcp || true
  ufw allow 443/tcp || true
  ufw --force enable || true
else
  warn "UFW ignorado (CE_SKIP_UFW=1)."
fi

JAVA_VER="$(java -version 2>&1 | head -1 || true)"
MVN_VER="$(mvn -version 2>/dev/null | head -1 || echo 'mvn n/d')"
NODE_VER="$(command -v node >/dev/null && node -v || echo 'node não instalado')"
PSQL_VER="$(sudo -u postgres psql -tAc 'SHOW server_version;' 2>/dev/null || true)"

log "Instalação concluída."
cat <<EOF

================================================================================
Resumo
================================================================================
Java:        ${JAVA_VER}
Maven:       ${MVN_VER}
Node:        ${NODE_VER}
PostgreSQL:  ${PSQL_VER}

Credenciais BD (guarda em local seguro):
  Utilizador:  ${DB_USER}
  Palavra-passe: ${DB_PASS}

JDBC Spring (application.properties / variável de ambiente):
  DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/consumoesperto
  DATABASE_USERNAME=${DB_USER}
  DATABASE_PASSWORD=${DB_PASS}

Evolution API (.env DATABASE_CONNECTION_URI, exemplo):
  postgresql://${DB_USER}:SENHA@127.0.0.1:5432/evolution_api?schema=public
EOF

if [[ -n "${CE_EVOLUTION_APIKEY_GENERATED}" ]]; then
  cat <<EOF

Evolution API (instalada em ${CE_EVOLUTION_HOME:-/opt/evolution-api}):
  AUTHENTICATION_API_KEY (usa também no Spring como EVOLUTION_APIKEY):
    ${CE_EVOLUTION_APIKEY_GENERATED}
  Arranque: cd ${CE_EVOLUTION_HOME:-/opt/evolution-api} && npm run start:prod
  Ajusta SERVER_URL no .env se a API for pública (HTTPS atrás de Nginx).
EOF
fi

cat <<'EOF'

Próximos passos — ConsumoEsperto (backend + frontend):
  1. Clonar o projeto e build:
       backend:   mvn -f backend/pom.xml -DskipTests package
       frontend:  cd frontend && npm ci && npm run build
  2. Em produção no Spring: export SPRING_PROFILES_ACTIVE=prod
     Variáveis obrigatórias típicas: DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD,
     JWT_SECRET, CORS_ALLOWED_PATTERNS (ex.: https://app.teudominio.com),
     EVOLUTION_APIKEY (igual à AUTHENTICATION_API_KEY da Evolution), GOOGLE_CLIENT_* se usares OAuth.
  3. Nginx: TLS, servir Angular (dist/) e proxy para API 8080; opcional proxy /evolution → 8081.
  4. Não exponhas /actuator nem Swagger na Internet (o profile prod já reduz exposição).
  5. systemd: serviços para java -jar backend e npm run start:prod na Evolution.

Este script prepara SO, PostgreSQL e (por defeito) o código da Evolution oficial; o deploy da app é manual.
================================================================================
EOF
