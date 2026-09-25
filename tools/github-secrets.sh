#!/bin/sh
# Печатает значения трёх секретов GitHub Actions для подписи FOCUS.
# Запуск из папки focus (там, где лежит папка signing): sh tools/github-secrets.sh
set -e
store=$(grep '^storePassword=' signing/local.properties | cut -d= -f2-)
key=$(grep '^keyPassword=' signing/local.properties | cut -d= -f2-)
b64=$(base64 < signing/personal.jks | tr -d '\n')
printf '\n1) FOCUS_KEYSTORE_PASSWORD\n   %s\n\n2) FOCUS_KEY_PASSWORD\n   %s\n\n3) FOCUS_KEYSTORE_BASE64\n   %s\n\n' "$store" "$key" "$b64"
echo 'Добавьте их здесь: https://github.com/romanorekhofff-sys/urban-winner/settings/secrets/actions/new'
