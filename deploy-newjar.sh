#!/bin/bash
set -e
TS=$(date +%Y%m%d_%H%M%S)
cd /home/mao/nac
cp app/nac-auth-1.0-SNAPSHOT.jar app/nac-auth.bak-$TS.jar
echo "backup -> app/nac-auth.bak-$TS.jar"
cp /tmp/nac-auth-new.jar app/nac-auth-1.0-SNAPSHOT.jar
md5sum app/nac-auth-1.0-SNAPSHOT.jar

# 关闭内置 EAPOL 桥（Linux pcap4j 收不到客户端 EAPOL，且存在伪造认证器风险）
sed -i 's/--eapol.enabled=true --eapol.device=enp3s0/--eapol.enabled=false/' nac.sh
grep 'nac-auth:' nac.sh

bash nac.sh stop nac-auth
sleep 2
bash nac.sh start nac-auth
sleep 8
echo '=== process ==='
ps -eo pid,lstart,cmd | grep 'app/nac-auth' | grep -v grep
echo '=== last 30 log lines ==='
tail -30 logs/nac-auth.out
