# Portas da stack ConsumoEsperto (dev / VPS sem choque com Apache 80/443 ou serviços em 8080/8081/4200).
# Outros scripts podem fazer: . "$PSScriptRoot\stack-ports.ps1"
$global:CE_PORT_EVOLUTION = 18080
$global:CE_PORT_SPRING = 18081
$global:CE_PORT_FRONTEND = 14200
$global:CE_PORT_NGROK = 14040
