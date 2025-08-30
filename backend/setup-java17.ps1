# =============================================================================
# CONFIGURADOR JAVA 17 PARA PROJETO CONSUMO ESPERTO (PowerShell)
# =============================================================================
# Este script configura o Java 17 da pasta tools apenas para este projeto
# =============================================================================

Write-Host "Configurando Java 17 para o projeto ConsumoEsperto..." -ForegroundColor Green
Write-Host ""

# Verificar se a pasta tools existe
$javaPath = "..\tools\java\ms-17.0.15"
if (-not (Test-Path $javaPath)) {
    Write-Host "ERRO: Pasta tools\java\ms-17.0.15 não encontrada!" -ForegroundColor Red
    Write-Host "Execute primeiro o script de instalação de ferramentas." -ForegroundColor Yellow
    Read-Host "Pressione Enter para sair"
    exit 1
}

# Configurar JAVA_HOME para este projeto
$env:JAVA_HOME = (Resolve-Path $javaPath).Path
Write-Host "JAVA_HOME configurado: $env:JAVA_HOME" -ForegroundColor Cyan

# Adicionar Java ao PATH temporariamente
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
Write-Host "PATH atualizado com Java 17" -ForegroundColor Cyan

# Verificar versão do Java
Write-Host ""
Write-Host "Verificando versão do Java..." -ForegroundColor Yellow
& "$env:JAVA_HOME\bin\java.exe" -version

Write-Host ""
Write-Host "=============================================================================" -ForegroundColor Green
Write-Host "CONFIGURAÇÃO CONCLUÍDA!" -ForegroundColor Green
Write-Host "=============================================================================" -ForegroundColor Green
Write-Host ""
Write-Host "Agora você pode executar:" -ForegroundColor White
Write-Host "  mvn clean compile" -ForegroundColor Cyan
Write-Host "  mvn spring-boot:run" -ForegroundColor Cyan
Write-Host ""
Write-Host "NOTA: Esta configuração é válida apenas para esta sessão do PowerShell." -ForegroundColor Yellow
Write-Host "Para uma nova sessão, execute este script novamente." -ForegroundColor Yellow
Write-Host "=============================================================================" -ForegroundColor Green
Write-Host ""

# Manter a sessão ativa
Write-Host "Configuração concluída! Execute seus comandos Maven agora." -ForegroundColor Green
