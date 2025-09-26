# Script para configurar credenciais do Mercado Pago
# Execute este script para inserir as credenciais no banco

Write-Host "🔧 Configurando credenciais do Mercado Pago..." -ForegroundColor Yellow

# Credenciais de exemplo (substitua pelas suas reais)
$clientId = "SEU_CLIENT_ID_AQUI"
$clientSecret = "SEU_CLIENT_SECRET_AQUI"

# Verificar se as credenciais foram fornecidas
if ($clientId -eq "SEU_CLIENT_ID_AQUI" -or $clientSecret -eq "SEU_CLIENT_SECRET_AQUI") {
    Write-Host "❌ ERRO: Você precisa fornecer suas credenciais reais do Mercado Pago!" -ForegroundColor Red
    Write-Host "`n📝 Como obter as credenciais:" -ForegroundColor Cyan
    Write-Host "   1. Acesse: https://www.mercadopago.com.br/developers" -ForegroundColor White
    Write-Host "   2. Faça login na sua conta" -ForegroundColor White
    Write-Host "   3. Crie uma nova aplicação" -ForegroundColor White
    Write-Host "   4. Copie o Client ID e Client Secret" -ForegroundColor White
    Write-Host "   5. Edite este script e substitua as credenciais" -ForegroundColor White
    Write-Host "`n💡 Ou configure via interface web:" -ForegroundColor Yellow
    Write-Host "   https://85766d45517b.ngrok-free.app/bank-config" -ForegroundColor White
    exit 1
}

# SQL para inserir as credenciais
$sql = @"
INSERT INTO bank_api_configs (
    id,
    nome,
    tipo_banco,
    client_id,
    client_secret,
    api_url,
    redirect_uri,
    scope,
    ativo,
    usuario_id,
    data_criacao,
    data_atualizacao
) VALUES (
    1,
    'Mercado Pago - Configuração Principal',
    'MERCADO_PAGO',
    '$clientId',
    '$clientSecret',
    'https://api.mercadopago.com',
    'https://85766d45517b.ngrok-free.app/api/auth/mercadopago/callback',
    'read write',
    true,
    1,
    NOW(),
    NOW()
) ON CONFLICT (id) DO UPDATE SET
    client_id = EXCLUDED.client_id,
    client_secret = EXCLUDED.client_secret,
    data_atualizacao = NOW();
"@

# Salvar SQL em arquivo temporário
$sqlFile = "temp_config.sql"
$sql | Out-File -FilePath $sqlFile -Encoding UTF8

Write-Host "📝 Executando SQL no banco de dados..." -ForegroundColor Blue

try {
    # Executar SQL no PostgreSQL
    psql -h localhost -U postgres -d consumoesperto -f $sqlFile
    
    Write-Host "✅ Credenciais configuradas com sucesso!" -ForegroundColor Green
    Write-Host "🔄 Agora acesse o dashboard para ver os dados reais!" -ForegroundColor Cyan
    
} catch {
    Write-Host "❌ Erro ao executar SQL:" -ForegroundColor Red
    Write-Host "   $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "`n💡 Execute manualmente no pgAdmin ou psql:" -ForegroundColor Yellow
    Write-Host "   $sql" -ForegroundColor White
} finally {
    # Limpar arquivo temporário
    if (Test-Path $sqlFile) {
        Remove-Item $sqlFile
    }
}

Write-Host "`n🏁 Script finalizado!" -ForegroundColor Green
