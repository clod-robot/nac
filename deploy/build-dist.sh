#!/bin/bash
# build-dist.sh — 在源服务器上归集所有文件，生成 nac-dist.tar.gz 部署包
# 产物包含：应用 jar、证书、脚本、前端、SQL、JDK、Nacos、Kafka、ZK、一键部署脚本
set -e
DIST=/home/mao/nac-dist
rm -rf "$DIST"
mkdir -p "$DIST"/{app,certs,scripts,frontend,sql,middleware}

echo "==== 1. 归集应用 jar（仅最新版本）===="
cp /home/mao/nac/app/nac-auth-1.0-SNAPSHOT.jar    "$DIST/app/"
cp /home/mao/nac/app/nac-gateway-1.0-SNAPSHOT.jar "$DIST/app/"
cp /home/mao/nac/app/nac-config-1.0-SNAPSHOT.jar  "$DIST/app/"
cp /home/mao/nac/app/nac-device-1.0-SNAPSHOT.jar  "$DIST/app/"
cp /home/mao/nac/app/nac-user-1.0-SNAPSHOT.jar    "$DIST/app/"
cp /home/mao/nac/app/nac-log-1.0-SNAPSHOT.jar     "$DIST/app/"
cp /home/mao/nac/app/nac-monitor-1.0-SNAPSHOT.jar "$DIST/app/"
echo "  app: $(du -sh $DIST/app | cut -f1)"

echo "==== 2. 归集证书 ===="
cp /home/mao/nac/certs/nac-ca.key              "$DIST/certs/"
cp /home/mao/nac/certs/nac-ca.pem              "$DIST/certs/"
cp /home/mao/nac/certs/eap-tls-clients.jks    "$DIST/certs/"
# peap.jks 由 ensure-server-cert.sh 启动时自动生成，不放包（避免 IP 写死）
echo "  certs: $(du -sh $DIST/certs | cut -f1)"

echo "==== 3. 归集脚本 ===="
cp /home/mao/nac/nac.sh                 "$DIST/scripts/"
cp /home/mao/nac/ensure-server-cert.sh  "$DIST/scripts/"
cp /home/mao/nac/boot-after-reboot.sh   "$DIST/scripts/"
echo "  scripts done"

echo "==== 4. 归集前端 ===="
cp -r /usr/share/nginx/html/nce "$DIST/frontend/"
cp /etc/nginx/conf.d/nce        "$DIST/scripts/nginx-nce.conf" 2>/dev/null || \
  cp /etc/nginx/sites-enabled/nce "$DIST/scripts/nginx-nce.conf" 2>/dev/null || true
echo "  frontend: $(du -sh $DIST/frontend | cut -f1)"

echo "==== 5. 导出数据库 SQL（表结构 + 初始数据）===="
mysqldump -uroot -p'${SUDO_PW}' --databases nac_db \
  --single-transaction --routines --triggers \
  --ignore-table=nac_db.sys_log --ignore-table=nac_db.sys_operation_log \
  --ignore-table=nac_db.sys_phone_audit --ignore-table=nac_db.sys_phone_record \
  > "$DIST/sql/nac_db.sql" 2>/dev/null
# 单独导出大表结构（不含数据，部署后自动产生）
mysqldump -uroot -p'${SUDO_PW}' nac_db --no-data \
  sys_log sys_operation_log sys_phone_audit sys_phone_record sys_radius_session \
  >> "$DIST/sql/nac_db.sql" 2>/dev/null
echo "  sql: $(du -sh $DIST/sql | cut -f1)"

echo "==== 6. 归集中间件（排除 logs/data）===="
cp -r /home/mao/software/jdk1.8.0_202 "$DIST/middleware/jdk"
echo "  jdk: $(du -sh $DIST/middleware/jdk | cut -f1)"

mkdir -p "$DIST/middleware/nacos"
cp -r /home/mao/software/nacos/bin    "$DIST/middleware/nacos/"
cp -r /home/mao/software/nacos/conf   "$DIST/middleware/nacos/"
cp -r /home/mao/software/nacos/target "$DIST/middleware/nacos/"
cp /home/mao/software/nacos/LICENSE /home/mao/software/nacos/NOTICE "$DIST/middleware/nacos/" 2>/dev/null || true
echo "  nacos: $(du -sh $DIST/middleware/nacos | cut -f1)"

cp -r /home/mao/software/kafka "$DIST/middleware/kafka"
rm -rf "$DIST/middleware/kafka/logs"
echo "  kafka: $(du -sh $DIST/middleware/kafka | cut -f1)"

cp -r /home/mao/software/zookeeper "$DIST/middleware/zookeeper"
rm -rf "$DIST/middleware/zookeeper/data" 2>/dev/null || true
echo "  zookeeper: $(du -sh $DIST/middleware/zookeeper | cut -f1)"

echo "==== 7. 生成版本信息 ===="
cat > "$DIST/VERSION" << EOF
NAC 全量部署包
构建时间: $(date '+%Y-%m-%d %H:%M:%S')
源服务器: $(hostname)
应用版本: 1.0-SNAPSHOT (含 EAP-TLS + 证书管理 API)
EOF
cat "$DIST/VERSION"

echo "==== 8. 打包 ===="
cd /home/mao
tar czf nac-dist.tar.gz nac-dist/
echo "部署包: /home/mao/nac-dist.tar.gz ($(du -sh /home/mao/nac-dist.tar.gz | cut -f1))"
echo "==== 构建完成 ===="
