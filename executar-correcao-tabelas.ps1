Write-Host "Executando correcao das tabelas..." -ForegroundColor Green

$sqlFile = "corrigir-estrutura-tabelas.sql"
if (Test-Path $sqlFile) {
    Write-Host "Arquivo SQL encontrado: $sqlFile" -ForegroundColor Green
    
    try {
        $env:PGPASSWORD = "postgres"
        $result = psql -h localhost -U postgres -d consumo_esperto -f $sqlFile 2>&1
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host "Correcao das tabelas executada com sucesso!" -ForegroundColor Green
            Write-Host $result -ForegroundColor White
        } else {
            Write-Host "Erro ao executar correcao das tabelas" -ForegroundColor Red
            Write-Host $result -ForegroundColor Yellow
        }
    } catch {
        Write-Host "Erro ao executar psql: $($_.Exception.Message)" -ForegroundColor Red
    }
} else {
    Write-Host "Arquivo nao encontrado: $sqlFile" -ForegroundColor Red
}

Write-Host "`nCORRECAO DAS TABELAS CONCLUIDA!" -ForegroundColor Green