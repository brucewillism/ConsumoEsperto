#!/bin/bash
# =========================================================
#         🚀 CONSUMOESPERTO AUTO DEPLOY SYSTEM 🚀
# =========================================================
#         Deploy automático FULL STACK - Versão Ultra Safe
#         Angular + Spring Boot + Docker + PostgreSQL
# =========================================================

echo "========================================="
echo "🧹 LIMPANDO PORTAS E PROCESSOS PRESOS..."
echo "========================================="

# 1. Derruba o Docker limpando volumes órfãos e containers antigos
docker compose down --remove-orphans

# 2. Força a liberação das portas matando qualquer processo zumbi (Java/Node/Nginx) no host
# Porta 8087 (Backend), 8181 (Frontend) e 8585 (Evolution API)
for port in 8087 8181 8585; do
    PID=$(sudo lsof -t -i:$port)
    if [ ! -z "$PID" ]; then
        echo "🚨 Encontrado processo zumbi $PID na porta $port. Eliminando..."
        sudo kill -9 $PID
    fi
done

echo "✅ Portas limpas! Iniciando build do ambiente..."

# 3. O restante do seu script original continua daqui para baixo...
# Exemplo:
# git pull
# docker compose up -d --build


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
echo "     🚀 CONSUMOESPERTO DEPLOY AUTOMÁTICO 🚀"
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

# ---------- BLINDAGEM DE PORTA (O SEGREDO) ----------
echo -e "${YELLOW}🧹 Iniciando limpeza profunda de rede e portas...${NC}"

# 1. Derruba o ambiente atual limpando containers órfãos
echo -e "${CYAN}   -> Parando containers do projeto...${NC}"
docker compose down --remove-orphans || true

# 2. Caça e mata qualquer processo Java ou zumbi na porta 8087 do Host
echo -e "${CYAN}   -> Desalocando a porta 8087 no sistema operacional...${NC}"
sudo fuser -k 8087/tcp || true
sudo lsof -t -i :8087 | xargs kill -9 2>/dev/null || true

# 3. Pequena pausa para o kernel do Linux processar o encerramento do socket de rede
sleep 2

# ---------- BUILD E DEPLOY ----------
echo -e "${YELLOW}🏗️  Reconstruindo imagens e subindo os serviços...${NC}"

# Executa o compose forçando a recriação total para evitar o estado "Created" travado
docker compose up -d --build --force-recreate

echo -e "${GREEN}✅ Ambiente reconstruído com sucesso!${NC}"
echo ""

# ---------- LIMPEZA DE CACHE ----------
echo -e "${YELLOW}🧹 Limpando imagens antigas e não utilizadas...${NC}"
docker image prune -f
echo -e "${GREEN}✅ Limpeza concluída.${NC}"
echo ""

# ---------- STATUS FINAL ----------
echo -e "${PURPLE}=====================================================${NC}"
echo -e "${GREEN}        ✅ DEPLOY FINALIZADO COM SUCESSO ✅        ${NC}"
echo -e "${PURPLE}=====================================================${NC}"
echo ""

echo -e "${BLUE}📦 Containers ativos:${NC}"
docker compose ps

echo ""
echo -e "${GREEN}🌐 Aplicação online!${NC}"
echo -e "${CYAN}⚡ Backend Spring Boot ativo na porta 8087${NC}"
echo -e "${CYAN}🎨 Frontend Angular ativo na porta 8181${NC}"
echo -e "${CYAN}🐳 Docker saudável e rodando.${NC}"
