#!/bin/bash
set -e
rm -f /tmp/peap.p12 /tmp/peap-all.pem /tmp/peap-ca.pem
keytool -importkeystore -srckeystore /home/mao/nac/certs/peap.jks -srcstorepass NceRadius@2026 \
  -destkeystore /tmp/peap.p12 -deststoretype PKCS12 -deststorepass NceRadius@2026 -noprompt >/dev/null 2>&1
openssl pkcs12 -in /tmp/peap.p12 -nokeys -passin pass:NceRadius@2026 -out /tmp/peap-all.pem 2>/dev/null
# chain order: leaf first, root CA last -> extract last cert block
awk 'BEGIN{n=0} /BEGIN CERT/{n++} {buf[n]=buf[n] $0 "\n"} END{printf "%s", buf[n]}' /tmp/peap-all.pem > /tmp/peap-ca.pem
chmod 644 /tmp/peap-ca.pem
echo "openssl: $(openssl version)"
echo "--- all certs subjects ---"
openssl crl2pkcs7 -nocrl -certfile /tmp/peap-all.pem 2>/dev/null | openssl pkcs7 -print_certs -noout 2>/dev/null | grep -E 'subject|issuer'
echo "--- extracted CA file ---"
grep -c 'BEGIN CERT' /tmp/peap-ca.pem
openssl x509 -in /tmp/peap-ca.pem -noout -subject -issuer -dates
