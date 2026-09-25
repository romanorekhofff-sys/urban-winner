# Печатает значения трёх секретов GitHub Actions для подписи FOCUS.
# Запуск: откройте PowerShell в папке focus (там, где лежит папка signing) и выполните:
#   powershell -ExecutionPolicy Bypass -File tools\github-secrets.ps1
$ErrorActionPreference = 'Stop'
$props = Get-Content 'signing\local.properties' | Where-Object { $_ -match '=' } | ForEach-Object { $k, $v = $_ -split '=', 2; @{ $k.Trim() = $v.Trim() } }
$store = ($props | ForEach-Object { $_['storePassword'] } | Where-Object { $_ })[0]
$key   = ($props | ForEach-Object { $_['keyPassword'] }   | Where-Object { $_ })[0]
$b64   = [Convert]::ToBase64String([IO.File]::ReadAllBytes('signing\personal.jks'))
Write-Host ''
Write-Host '1) Имя секрета: FOCUS_KEYSTORE_PASSWORD'
Write-Host "   Значение:    $store"
Write-Host ''
Write-Host '2) Имя секрета: FOCUS_KEY_PASSWORD'
Write-Host "   Значение:    $key"
Write-Host ''
Write-Host '3) Имя секрета: FOCUS_KEYSTORE_BASE64'
Write-Host '   Значение (одна длинная строка, уже скопирована в буфер обмена):'
Write-Host "   $b64"
Set-Clipboard -Value $b64
Write-Host ''
Write-Host 'Добавьте их здесь: https://github.com/romanorekhofff-sys/urban-winner/settings/secrets/actions/new'
