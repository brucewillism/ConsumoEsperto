# =============================================================================
# Script PowerShell para configurar JAVA_HOME para o projeto ConsumoEsperto
# =============================================================================
# Este script configura a variável JAVA_HOME para apontar para a instalação
# do Java na pasta tools/java/ms-17.0.15
# =============================================================================

Write-Host ""
Write-Host "=============================================================================" -ForegroundColor Cyan
Write-Host "Configurando JAVA_HOME para ConsumoEsperto" -ForegroundColor Cyan
Write-Host "=============================================================================" -ForegroundColor Cyan
Write-Host ""

# Obtém o diretório atual do script
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$JavaHomeLocal = Join-Path $ScriptDir "tools\java\ms-17.0.15"

# Verifica se o diretório Java existe
if (-not (Test-Path $JavaHomeLocal)) {
    Write-Host "[ERRO] Diretório Java não encontrado: $JavaHomeLocal" -ForegroundColor Red
    Write-Host ""
    Write-Host "Por favor, verifique se o Java está instalado na pasta tools/java/" -ForegroundColor Yellow
    Write-Host ""
    Read-Host "Pressione Enter para sair"
    exit 1
}

# Verifica se java.exe existe
$JavaExe = Join-Path $JavaHomeLocal "bin\java.exe"
if (-not (Test-Path $JavaExe)) {
    Write-Host "[ERRO] java.exe não encontrado em: $JavaExe" -ForegroundColor Red
    Write-Host ""
    Write-Host "Por favor, verifique se a instalação do Java está completa." -ForegroundColor Yellow
    Write-Host ""
    Read-Host "Pressione Enter para sair"
    exit 1
}

# Configura JAVA_HOME para esta sessão
$env:JAVA_HOME = $JavaHomeLocal
$env:PATH = "$JavaHomeLocal\bin;$env:PATH"

Write-Host "[OK] JAVA_HOME configurado: $env:JAVA_HOME" -ForegroundColor Green
Write-Host "[OK] Java adicionado ao PATH" -ForegroundColor Green
Write-Host ""

# Verifica a versão do Java
Write-Host "Verificando versão do Java..." -ForegroundColor Cyan
& "$JavaExe" -version
Write-Host ""

# Cria arquivo .env para uso em IDEs
Write-Host "Criando arquivo .env para configuração do IDE..." -ForegroundColor Cyan
$EnvContent = @"
# Configuração JAVA_HOME para ConsumoEsperto
# Gerado automaticamente por configurar-java-home.ps1
JAVA_HOME=$JavaHomeLocal
"@
$EnvContent | Out-File -FilePath ".env" -Encoding UTF8
Write-Host "[OK] Arquivo .env criado" -ForegroundColor Green
Write-Host ""

# Instruções para configuração permanente
Write-Host "=============================================================================" -ForegroundColor Cyan
Write-Host "IMPORTANTE: Configuração da Sessão Atual" -ForegroundColor Yellow
Write-Host "=============================================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "A variável JAVA_HOME foi configurada APENAS para esta sessão do PowerShell." -ForegroundColor Yellow
Write-Host ""
Write-Host "Para configurar permanentemente:" -ForegroundColor Cyan
Write-Host ""
Write-Host "1. Abra PowerShell como Administrador" -ForegroundColor White
Write-Host ""
Write-Host "2. Execute o seguinte comando:" -ForegroundColor White
Write-Host "   [System.Environment]::SetEnvironmentVariable('JAVA_HOME', '$JavaHomeLocal', 'Machine')" -ForegroundColor Gray
Write-Host ""
Write-Host "3. Adicione ao PATH do sistema:" -ForegroundColor White
Write-Host "   `$currentPath = [System.Environment]::GetEnvironmentVariable('Path', 'Machine')" -ForegroundColor Gray
Write-Host "   [System.Environment]::SetEnvironmentVariable('Path', `"`$currentPath;`$env:JAVA_HOME\bin`", 'Machine')" -ForegroundColor Gray
Write-Host ""
Write-Host "=============================================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Configuração concluída! Você pode usar o Java agora." -ForegroundColor Green
Write-Host ""
Read-Host "Pressione Enter para continuar"

