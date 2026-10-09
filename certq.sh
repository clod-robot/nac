#!/bin/bash
set -x
which java keytool openssl
java -version 2>&1 | head -2
keytool -list -v -keystore /home/mao/nac/certs/peap.jks -storepass NceRadius@2026 2>&1 | head -40
echo "=== TLS/PEAP init logs ==="
grep -aiE 'tls|peap|jks|keystore|cert' /home/mao/nac/logs/nac-auth.out | tail -15
