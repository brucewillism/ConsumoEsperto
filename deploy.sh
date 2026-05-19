#!/bin/bash

# =========================================================
#        🚀 CONSUMOESPERTO AUTO DEPLOY SYSTEM 🚀
# =========================================================
#        Deploy automático FULL STACK
#        Angular + Spring Boot + Docker + PostgreSQL
# =========================================================

set -e

clear

# ---------- CORES ----------
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[1;34m'
YELLOW='\033[1;33m'
CYAN='\033[1;36m'
PURPLE='\033[1;35m'
NC='\033[0m'

# ---------- ANIMAÇÃO ----------
spinner() {
    local pid=$!
    local delay=0.08
    local spinstr='⠋⠙⠸⠴⠦⠇'

    while [ "$(ps a | awk '{print $1}' | grep $pid)" ]; do
        local temp=${spinstr#?}
        printf " ${CYAN}[%c]${NC}  " "$spinstr"
        local spinstr=$temp${spinstr%"$temp"}
        sleep $delay
        printf "\b\b\b\b\b\b"
    done

    printf "    \b\b\b\b"
}

# ---------- HEADER ----------
echo -e "${PURPLE}"
echo "====================================================="
echo "      🚀 CONSUMOESPERTO DEPLOY AUTOMÁTICO 🚀"
echo "====================================================="
echo -e "${NC}"

echo -e "${BLUE}📅 Data:${NC} $(date)"
echo -e "${BLUE}🖥️  Servidor:${NC} $(hostname)"
echo -e "${BLUE}👤 Usuário:${NC} $(whoami)"
echo ""

# ---------- VERIFICAÇÕES ----------
echo -e "${YELLOW}🔎 Verificando Docker...${NC}"

if ! command -v docker &> /dev/null
then
    echo -e "${RED}❌ Docker não encontrado.${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Docker OK${NC}"

echo ""

# ---------- GIT PULL ----------
echo -e "${YELLOW}📥 Atualizando código do GitHub...${NC}"

git pull origin main &
spinner

echo -e "${GREEN}✅ Código atualizado${NC}"
echo ""

# ---------- PARANDO CONTAINERS ----------
echo -e "${YELLOW}🛑 Derrubando containers antigos...${NC}"

docker compose down &
spinner

echo -e "${GREEN}✅ Containers finalizados${NC}"
echo ""

# ---------- BUILD ----------
echo -e "${YELLOW}🏗️ Reconstruindo ambiente Docker...${NC}"

docker compose up --build -d &
spinner

echo -e "${GREEN}✅ Ambiente reconstruído${NC}"
echo ""

# ---------- LIMPEZA ----------
echo -e "${YELLOW}🧹 Limpando imagens antigas...${NC}"

docker image prune -f &
spinner

echo -e "${GREEN}✅ Limpeza concluída${NC}"
echo ""

# ---------- STATUS ----------
echo -e "${YELLOW}📦 Containers ativos:${NC}"
docker ps

echo ""

# ---------- FINAL ----------
echo -e "${GREEN}"
echo "====================================================="
echo "        ✅ DEPLOY FINALIZADO COM SUCESSO ✅"
echo "====================================================="
echo -e "${NC}"

echo -e "${CYAN}🌐 Aplicação online${NC}"
echo -e "${CYAN}⚡ Backend Spring Boot ativo${NC}"
echo -e "${CYAN}🎨 Frontend Angular ativo${NC}"
echo -e "${CYAN}🐳 Docker saudável${NC}"
echo ""

# ---------- SOM OPCIONAL ----------
printf '\a'
