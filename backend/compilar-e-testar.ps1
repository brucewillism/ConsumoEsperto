Write-Host "🔨 COMPILANDO E TESTANDO - MODO DE TESTE" -ForegroundColor Yellow
Write-Host ""

Write-Host "1. Compilando o projeto..." -ForegroundColor Blue
mvn clean compile -q

if ($LASTEXITCODE -eq 0) {
    Write-Host "   ✅ Compilação bem-sucedida!" -ForegroundColor Green
} else {
    Write-Host "   ❌ Erro na compilação!" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "2. Executando o projeto..." -ForegroundColor Blue
Write-Host "   Pressione Ctrl+C para parar o backend" -ForegroundColor Gray
Write-Host "   Em seguida, execute: mvn spring-boot:run" -ForegroundColor Cyan

Write-Host ""
Write-Host "3. Teste a aplicação:" -ForegroundColor Blue
Write-Host "   - Acesse: https://85766d45517b.ngrok-free.app/bank-config" -ForegroundColor Cyan
Write-Host "   - Deve mostrar 'Configuração encontrada' em vez de erro" -ForegroundColor Cyan

Write-Host ""
Write-Host "🎯 RESULTADO ESPERADO:" -ForegroundColor Blue
Write-Host "   ✅ Sem mais erros 401 'Unauthorized use of live credentials'" -ForegroundColor Green
Write-Host "   ✅ API do Mercado Pago aceita as credenciais de teste" -ForegroundColor Green
Write-Host "   ✅ Dados de teste são carregados no dashboard" -ForegroundColor Green
