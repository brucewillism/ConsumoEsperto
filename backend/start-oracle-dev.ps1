Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  ConsumoEsperto - Oracle Development" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "1. Verificando se o Oracle esta rodando..." -ForegroundColor Yellow
$oraclePort = netstat -ano | Select-String ":1521"
if ($oraclePort) {
    Write-Host "[OK] Oracle esta rodando na porta 1521" -ForegroundColor Green
} else {
    Write-Host "[ERRO] Oracle nao esta rodando na porta 1521" -ForegroundColor Red
    Write-Host ""
    Write-Host "Para usar Docker:" -ForegroundColor Yellow
    Write-Host "  docker-compose up -d oracle" -ForegroundColor White
    Write-Host ""
    Write-Host "Para Oracle local:" -ForegroundColor Yellow
    Write-Host "  Verifique se o servico Oracle esta rodando" -ForegroundColor White
    Write-Host ""
    Read-Host "Pressione Enter para sair"
    exit 1
}

Write-Host ""
Write-Host "2. Configurando JAVA_HOME..." -ForegroundColor Yellow
$env:JAVA_HOME = "C:\Users\bruce.silva\.jdks\ms-17.0.15"
Write-Host "[OK] JAVA_HOME configurado: $env:JAVA_HOME" -ForegroundColor Green

Write-Host ""
Write-Host "3. Iniciando aplicacao Spring Boot..." -ForegroundColor Yellow
Write-Host "Perfil ativo: dev (Oracle)" -ForegroundColor White
Write-Host ""

mvn spring-boot:run -Dspring.profiles.active=dev

Write-Host ""
Write-Host "Aplicacao finalizada." -ForegroundColor Cyan
Read-Host "Pressione Enter para sair"
