#!/bin/bash
cd /home/mao
PW='${SUDO_PW}'
sd() { echo "$PW" | sudo -S -k "$@" 2>/dev/null; }

echo "===== 1) DB 迁移（幂等）====="
MYSQL_PWD='${MYSQL_PASSWORD}' mysql -h127.0.0.1 -unac nac < /home/mao/migrate_portal_url_params.sql
echo "migrate_rc=$?"
MYSQL_PWD='${MYSQL_PASSWORD}' mysql -h127.0.0.1 -unac nac -e "SHOW COLUMNS FROM sys_auth_log LIKE 'ssid'; SHOW COLUMNS FROM sys_online_session LIKE 'nas_name'; SELECT config_key,config_value FROM sys_portal_config WHERE config_key LIKE 'urlParam%';"

echo "===== 2) 解压源码 ====="
rm -rf /home/mao/build2 && mkdir -p /home/mao/build2
tar xzf /home/mao/backend-src.tgz -C /home/mao/build2

echo "===== 3) 构建 nac-auth / nac-log ====="
JAVA_HOME=/usr/local/jdk /home/mao/apache-maven-3.9.9/bin/mvn -q -f /home/mao/build2/backend/pom.xml -pl nac-auth,nac-log -am -DskipTests package > /home/mao/build2.log 2>&1
rc=$?
echo "build_rc=$rc"
if [ $rc -ne 0 ]; then echo "BUILD_FAILED"; tail -60 /home/mao/build2.log; exit 1; fi
ls -la /home/mao/build2/backend/nac-auth/target/*.jar /home/mao/build2/backend/nac-log/target/*.jar

echo "===== 4) 部署 jar（保留回退）====="
cp -f /home/mao/nac-auth.jar /home/mao/nac-auth.jar.bak-urlparam
cp -f /home/mao/nac-log.jar  /home/mao/nac-log.jar.bak-urlparam
cp -f /home/mao/build2/backend/nac-auth/target/nac-auth-1.0.0-SNAPSHOT.jar /home/mao/nac-auth.jar
cp -f /home/mao/build2/backend/nac-log/target/nac-log-1.0.0-SNAPSHOT.jar  /home/mao/nac-log.jar
ls -la /home/mao/nac-auth.jar /home/mao/nac-log.jar

echo "===== 5) 重启 auth / log ====="
sd fuser -k 8081/tcp
sd fuser -k 8084/tcp
sleep 3
/home/mao/restart-one.sh auth
/home/mao/restart-one.sh log
sleep 28
echo "===== 6) 端口检查 ====="
ss -ltn | grep -E ':8081|:8084'
echo "===== 7) 日志尾部 ====="
tail -5 /home/mao/auth.log
tail -5 /home/mao/log.log
echo "ALL_DONE"
