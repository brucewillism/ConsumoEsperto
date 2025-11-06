# Script para corrigir compatibilidade com Java 8
# Substitui métodos do Java 9+ por equivalentes do Java 8

Write-Host "🔧 Corrigindo compatibilidade com Java 8..." -ForegroundColor Yellow

# Função para substituir em um arquivo
function Fix-File {
    param($FilePath)
    
    if (Test-Path $FilePath) {
        Write-Host "  📝 Processando: $FilePath" -ForegroundColor Cyan
        
        $content = Get-Content $FilePath -Raw -Encoding UTF8
        
        # Substituir Optional.isEmpty() por !Optional.isPresent()
        $content = $content -replace '\.isEmpty\(\)', '.isPresent() == false'
        
        # Substituir Map.of() por HashMap
        $content = $content -replace 'Map\.of\(([^)]+)\)', 'new HashMap<String, Object>() {{ put($1); }}'
        
        # Substituir List.of() por Arrays.asList()
        $content = $content -replace 'List\.of\(([^)]+)\)', 'Arrays.asList($1)'
        
        # Adicionar imports necessários se não existirem
        if ($content -match 'new HashMap<String, Object>' -and $content -notmatch 'import java\.util\.HashMap') {
            $content = $content -replace '(import java\.util\.\w+;)', '$1' + "`nimport java.util.HashMap;"
        }
        
        if ($content -match 'Arrays\.asList' -and $content -notmatch 'import java\.util\.Arrays') {
            $content = $content -replace '(import java\.util\.\w+;)', '$1' + "`nimport java.util.Arrays;"
        }
        
        Set-Content $FilePath -Value $content -Encoding UTF8
        Write-Host "    ✅ Corrigido" -ForegroundColor Green
    }
}

# Lista de arquivos para corrigir
$files = @(
    "src/main/java/com/consumoesperto/service/MercadoPagoService.java",
    "src/main/java/com/consumoesperto/service/MercadoPagoBankService.java",
    "src/main/java/com/consumoesperto/service/ItauBankService.java",
    "src/main/java/com/consumoesperto/service/InterBankService.java",
    "src/main/java/com/consumoesperto/service/NubankBankService.java",
    "src/main/java/com/consumoesperto/service/BankApiService.java",
    "src/main/java/com/consumoesperto/controller/SetupController.java",
    "src/main/java/com/consumoesperto/controller/BankSynchronizationController.java",
    "src/main/java/com/consumoesperto/controller/BankController.java",
    "src/main/java/com/consumoesperto/config/SwaggerConfig.java"
)

# Processar cada arquivo
foreach ($file in $files) {
    Fix-File $file
}

Write-Host "🎉 Correções de compatibilidade com Java 8 concluídas!" -ForegroundColor Green
Write-Host "💡 Execute 'mvn clean compile -DskipTests' para verificar se os erros foram corrigidos." -ForegroundColor Blue
