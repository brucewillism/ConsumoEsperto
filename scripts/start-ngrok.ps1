# Script para iniciar o ngrok para o ConsumoEsperto
# Certifique-se de que o backend Spring Boot está rodando na porta 8080

Write-Host "Iniciando ngrok para ConsumoEsperto..." -ForegroundColor Green

# Verificar se o ngrok está instalado
$ngrokPath = Get-Command ngrok -ErrorAction SilentlyContinue
if (-not $ngrokPath) {
    Write-Host "ngrok não encontrado. Por favor, instale o ngrok primeiro." -ForegroundColor Red
    Write-Host "Download: https://ngrok.com/download" -ForegroundColor Yellow
    exit 1
}

# Verificar se a porta 8080 está em uso (backend rodando)
$port8080 = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue
if (-not $port8080) {
    Write-Host "Backend não está rodando na porta 8080. Inicie o backend primeiro." -ForegroundColor Red
    Write-Host "Execute: cd backend && ./mvnw spring-boot:run" -ForegroundColor Yellow
    exit 1
}

# Iniciar ngrok
Write-Host "Iniciando ngrok na porta 8080..." -ForegroundColor Yellow
ngrok http 8080

Write-Host "ngrok iniciado com sucesso!" -ForegroundColor Green
Write-Host "Use a URL fornecida pelo ngrok para configurar o frontend." -ForegroundColor Cyan
