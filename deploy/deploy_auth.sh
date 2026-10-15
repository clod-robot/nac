#!/bin/bash
cd /home/mao
ls -la nac-auth.jar.new
mv -f nac-auth.jar.new nac-auth.jar
pkill -f 'java -jar nac-auth.jar' 2>/dev/null
sleep 3
pkill -9 -f 'java -jar nac-auth.jar' 2>/dev/null
sleep 1
ENV="MYSQL_USER=nac MYSQL_PASSWORD=${MYSQL_PASSWORD} KAFKA_SERVERS=127.0.0.1:9092 NACOS_SERVER=127.0.0.1:8848 JAVA_HOME=/usr/local/jdk"
nohup env $ENV /usr/local/jdk/bin/java -jar nac-auth.jar --server.port=8081 > /home/mao/auth.log 2>&1 &
echo "started pid $!"
sleep 14
ss -ltn 2>/dev/null | grep ':8081 ' || echo "PORT_NOT_LISTENING"
echo "--- auth.log tail ---"
tail -n 8 /home/mao/auth.log
