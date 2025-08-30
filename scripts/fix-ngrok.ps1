# Script para corrigir problemas do ngrok
Write-Host "Corrigindo problemas do ngrok..." -ForegroundColor Yellow

# 1. Parar processos ngrok existentes
Write-Host "Parando processos ngrok existentes..." -ForegroundColor Cyan
Get-Process -Name "ngrok" -ErrorAction SilentlyContinue | Stop-Process -Force

# 2. Limpar cache do ngrok
Write-Host "Limpando cache do ngrok..." -ForegroundColor Cyan
$ngrokCachePath = "$env:USERPROFILE\.ngrok2"
if (Test-Path $ngrokCachePath) {
    Remove-Item -Path "$ngrokCachePath\ngrok.yml" -Force -ErrorAction SilentlyContinue
    Write-Host "Cache limpo" -ForegroundColor Green
}

# 3. Verificar se o ngrok esta instalado
$ngrokPath = Get-Command ngrok -ErrorAction SilentlyContinue
if ($ngrokPath) {
    Write-Host "ngrok encontrado em: $($ngrokPath.Source)" -ForegroundColor Green
    
    # 4. Mostrar versao atual
    $version = & ngrok version 2>$null
    Write-Host "Versao atual: $version" -ForegroundColor Cyan
    
    # 5. Tentar atualizar
    Write-Host "Tentando atualizar ngrok..." -ForegroundColor Yellow
    try {
        & ngrok update --log=stdout
        Write-Host "Atualizacao concluida" -ForegroundColor Green
    } catch {
        Write-Host "Nao foi possivel atualizar automaticamente" -ForegroundColor Yellow
    }
    
    # 6. Iniciar ngrok com configuracao limpa
    Write-Host "Iniciando ngrok..." -ForegroundColor Green
    Start-Process -FilePath "ngrok" -ArgumentList "http", "8080" -WindowStyle Normal
    
} else {
    Write-Host "ngrok nao encontrado no PATH" -ForegroundColor Red
    Write-Host "Instale o ngrok ou adicione ao PATH" -ForegroundColor Yellow
}

Write-Host "Script concluido!" -ForegroundColor Green
Write-Host "Se ainda houver problemas, tente:" -ForegroundColor Cyan
Write-Host "   1. Reinstalar o ngrok" -ForegroundColor White
Write-Host "   2. Verificar configuracoes de firewall" -ForegroundColor White
Write-Host "   3. Usar ngrok config add-authtoken SEU_TOKEN" -ForegroundColor White
