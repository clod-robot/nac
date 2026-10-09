#!/bin/bash
# 以安静日志重启 nac-auth（com.nac=WARN），暴露真实处理上限
export JAVA_HOME=$HOME/software/jdk1.8.0_202
export PATH=$JAVA_HOME/bin:$PATH
JARF=/home/mao/nac/app/nac-auth-1.0-SNAPSHOT.jar
LOGF=/home/mao/nac/logs/nac-auth-quiet.out

test -f "$JARF" || { echo "JAR MISSING: $JARF"; exit 1; }
pkill -f "app/nac-auth-1.0-SNAPSHOT.jar"
for i in $(seq 1 15); do
  pgrep -f "app/nac-auth-1.0-SNAPSHOT.jar" >/dev/null || break
  sleep 1
done
pgrep -f "app/nac-auth-1.0-SNAPSHOT.jar" >/dev/null && pkill -9 -f "app/nac-auth-1.0-SNAPSHOT.jar"
sleep 2

nohup "$JAVA_HOME/bin/java" -server -Xms1024m -Xmx1024m -XX:+UseG1GC -jar "$JARF" --eapol.enabled=false --logging.level.com.nac=WARN --logging.level.org.pcap4j=WARN > "$LOGF" 2>&1 &
echo "nac-auth restarting (WARN), pid=$!"

for i in $(seq 1 60); do
  sleep 2
  if grep -q "Started AuthApplication" "$LOGF" 2>/dev/null; then
    echo "nac-auth READY after $((i*2))s"
    ss -ulnp | grep ":1812 " && exit 0
  fi
  if grep -qE "APPLICATION FAILED|BindException" "$LOGF" 2>/dev/null; then
    echo "START FAILED"; tail -30 "$LOGF"; exit 1
  fi
done
echo "TIMEOUT"; tail -30 "$LOGF"; exit 1
