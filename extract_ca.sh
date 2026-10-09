#!/bin/bash
set -e
# 从 peap.jks 导出完整链 PEM，按证书分片到 /tmp/cert_N.pem
keytool -list -rfc -keystore /home/mao/nac/certs/peap.jks -storepass 'NceRadius@2026' -alias peap 2>/dev/null \
  | awk 'BEGIN{n=0} /-----BEGIN CERTIFICATE-----/{n++} {print > ("/tmp/cert_" n ".pem")}'

echo "=== leaf (cert_1) ==="
openssl x509 -in /tmp/cert_1.pem -noout -subject -issuer 2>&1
echo "=== CA (cert_2) ==="
openssl x509 -in /tmp/cert_2.pem -noout -subject -issuer 2>&1

echo "=== verify server leaf against CA (cert_2) ==="
openssl verify -CAfile /tmp/cert_2.pem /tmp/cert_1.pem 2>&1 || echo "verify failed"

echo "=== verify client.crt against CA (cert_2) ==="
openssl verify -CAfile /tmp/cert_2.pem /tmp/client.crt 2>&1 || echo "client cert verify failed"

echo "=== copy CA to /tmp/server-ca.pem ==="
cp /tmp/cert_2.pem /tmp/server-ca.pem
ls -la /tmp/server-ca.pem
