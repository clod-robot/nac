#!/bin/bash
# ensure-server-cert.sh — 启动前确保 peap.jks 服务器证书 SAN 覆盖本机所有 IP
# 跨生产环境部署时自动适配不同 IP，客户端 ca.pem（= 统一 CA 证书）不变
set -e
CERT_DIR=/home/mao/nac/certs
JKS=$CERT_DIR/peap.jks
CA_KEY=$CERT_DIR/nac-ca.key
CA_PEM=$CERT_DIR/nac-ca.pem
STORE_PASS=NceRadius@2026
TMP=$(mktemp -d)
trap "rm -rf $TMP" EXIT

[ -f "$CA_KEY" ] || { echo "CA 密钥不存在: $CA_KEY"; exit 1; }
[ -f "$CA_PEM" ] || { echo "CA 证书不存在: $CA_PEM"; exit 1; }

# 1. 采集本机所有非回环 IPv4（去重排序）
IPS=$(ip -4 addr show 2>/dev/null | grep -oP 'inet \K[\d.]+' | grep -v '^127\.' | sort -u)
if [ -z "$IPS" ]; then
  echo "未检测到本机 IP，跳过证书更新"
  exit 0
fi
IP_LIST=$(echo "$IPS" | tr '\n' ' ')
echo "本机 IP: $IP_LIST"

# 2. 检查现有证书 SAN 是否已覆盖所有 IP（避免无谓重签）
NEED_REGEN=0
if [ -f "$JKS" ]; then
  keytool -exportcert -keystore "$JKS" -storepass "$STORE_PASS" -alias peap -rfc 2>/dev/null \
    | openssl x509 -noout -ext subjectAltName 2>/dev/null > "$TMP/san.txt" || true
  for ip in $IPS; do
    if ! grep -q "$ip" "$TMP/san.txt" 2>/dev/null; then
      NEED_REGEN=1
      echo "证书 SAN 缺少 IP: $ip，需要重签"
      break
    fi
  done
else
  NEED_REGEN=1
fi

if [ $NEED_REGEN -eq 0 ]; then
  echo "服务器证书 SAN 已覆盖所有本机 IP，无需更新"
  exit 0
fi

# 3. 生成新服务器证书（RSA 2048，SAN=所有 IP+127.0.0.1+DNS）
SAN_EXT="subjectAltName = DNS:localhost,DNS:radius.local,DNS:$(hostname),IP:127.0.0.1"
for ip in $IPS; do
  SAN_EXT="$SAN_EXT,IP:$ip"
done
echo "SAN: $SAN_EXT"

openssl genrsa -out "$TMP/server.key" 2048 2>/dev/null
openssl req -new -key "$TMP/server.key" -out "$TMP/server.csr" \
  -subj "/C=CN/ST=Guangdong/L=Guangzhou/O=NCE/OU=Network/CN=$(hostname)" 2>/dev/null

# 用 CA 签名，有效期 825 天（Chrome/Android 上限）
openssl x509 -req -in "$TMP/server.csr" -CA "$CA_PEM" -CAkey "$CA_KEY" -CAcreateserial \
  -out "$TMP/server.crt" -days 825 -sha256 -extfile <(echo "$SAN_EXT") 2>/dev/null

# 4. 打包为 PKCS12（服务器证书 + 私钥 + CA 链），再转为 JKS
openssl pkcs12 -export -inkey "$TMP/server.key" -in "$TMP/server.crt" \
  -certfile "$CA_PEM" -out "$TMP/server.p12" -passout pass:"$STORE_PASS" -name peap 2>/dev/null

# 若 JKS 已存在则备份
[ -f "$JKS" ] && cp "$JKS" "${JKS}.bak.$(date +%s)"

# 用 keytool 从 PKCS12 导入为 JKS（覆盖式重建）
rm -f "$JKS"
keytool -importkeystore -srckeystore "$TMP/server.p12" -srcstoretype PKCS12 \
  -srcstorepass "$STORE_PASS" -destkeystore "$JKS" -deststoretype JKS \
  -deststorepass "$STORE_PASS" -noprompt 2>/dev/null
chmod 640 "$JKS"

echo "服务器证书已更新: $JKS"
keytool -exportcert -keystore "$JKS" -storepass "$STORE_PASS" -alias peap -rfc 2>/dev/null \
  | openssl x509 -noout -subject -ext subjectAltName 2>/dev/null
