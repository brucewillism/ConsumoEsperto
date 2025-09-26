Write-Host "Configurando Mercado Pago..." -ForegroundColor Green

# Configurações do Mercado Pago
$CLIENT_ID = "4223603750190943"
$CLIENT_SECRET = "SEU_CLIENT_SECRET_AQUI"
$NGROK_URL = "https://262f3e49bd2d.ngrok-free.app"

Write-Host "Client ID: $CLIENT_ID" -ForegroundColor Yellow
Write-Host "Ngrok URL: $NGROK_URL" -ForegroundColor Yellow
Write-Host "Client Secret: $CLIENT_SECRET" -ForegroundColor Yellow

# Atualizar application.properties
$appPropsFile = "backend\src\main\resources\application.properties"
if (Test-Path $appPropsFile) {
    $content = Get-Content $appPropsFile -Raw
    $content = $content -replace 'bank\.api\.mercadopago\.client-id=.*', "bank.api.mercadopago.client-id=$CLIENT_ID"
    $content = $content -replace 'bank\.api\.mercadopago\.client-secret=.*', "bank.api.mercadopago.client-secret=$CLIENT_SECRET"
    $content = $content -replace 'ngrok\.url=.*', "ngrok.url=$NGROK_URL"
    Set-Content $appPropsFile $content -Encoding UTF8
    Write-Host "application.properties atualizado" -ForegroundColor Green
} else {
    Write-Host "Arquivo application.properties nao encontrado" -ForegroundColor Red
}

# Atualizar env-vars.txt
$envVarsFile = "backend\env-vars.txt"
if (Test-Path $envVarsFile) {
    $content = Get-Content $envVarsFile -Raw
    $content = $content -replace 'MERCADOPAGO_CLIENT_ID=.*', "MERCADOPAGO_CLIENT_ID=$CLIENT_ID"
    $content = $content -replace 'MERCADOPAGO_CLIENT_SECRET=.*', "MERCADOPAGO_CLIENT_SECRET=$CLIENT_SECRET"
    $content = $content -replace 'NGROK_URL=.*', "NGROK_URL=$NGROK_URL"
    Set-Content $envVarsFile $content -Encoding UTF8
    Write-Host "env-vars.txt atualizado" -ForegroundColor Green
} else {
    Write-Host "Arquivo env-vars.txt nao encontrado" -ForegroundColor Red
}

Write-Host "`nConfiguracao do Mercado Pago concluida!" -ForegroundColor Green
Write-Host "IMPORTANTE: Substitua SEU_CLIENT_SECRET_AQUI pelo seu Client Secret real" -ForegroundColor Red
