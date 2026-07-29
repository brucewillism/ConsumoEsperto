# Build/test do backend com JDK 17 via Docker (quando Docker estiver disponível).
# Uso: .\scripts\mvn-docker.ps1 clean test
$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$backend = Join-Path $root "backend"

docker run --rm `
  -v "${backend}:/app" `
  -w /app `
  maven:3.9-eclipse-temurin-17 `
  mvn @args
