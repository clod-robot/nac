#!/bin/bash
echo "===== LISTEN PORTS ====="
ss -tulnp 2>/dev/null | grep -E '3306|6379|9092|8848|2181|9000|9001' || echo "(none matched)"
echo "===== MIDDLEWARE PROCS ====="
ps -eo pid,user,cmd | grep -E 'mysqld|redis-server|kafka.Kafka|zookeeper|nacos' | grep -v grep | cut -c1-160
echo "===== libpcap ====="
dpkg -l 2>/dev/null | grep -i pcap || echo "(no pcap pkg)"
echo "===== JDK8 ====="
~/software/jdk1.8.0_202/bin/java -version 2>&1
echo "===== MYSQL nac_db ====="
mysql -uroot -pMAO5201314sm -h127.0.0.1 -e "SELECT VERSENSION(); SHOW DATABASES LIKE 'nac_db'; USE nac_db; SELECT username,auth_type,enabled FROM sys_radius_user; SELECT COUNT(*) AS user_cnt FROM sys_user;" 2>&1 | grep -v "Using a password"
echo "===== REDIS ====="
redis-cli -a MAO5201314sm PING 2>/dev/null
echo "===== KAFKA topics ====="
ls ~/software/kafka/bin/kafka-topics.sh 2>/dev/null && ~/software/kafka/bin/kafka-topics.sh --bootstrap-server 127.0.0.1:9092 --list 2>/dev/null
echo "===== NACOS proc ====="
pgrep -f nacos-server.jar || echo "nacos not running"
echo "===== net iface ====="
ip -4 -o addr | awk '{print $2,$4}'
