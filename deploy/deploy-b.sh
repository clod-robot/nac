#!/bin/bash
TS=$(date +%Y%m%d_%H%M%S)
cd /home/mao/nac
cp app/nac-auth-1.0-SNAPSHOT.jar app/nac-auth.bak-$TS.jar || true
echo "backup -> app/nac-auth.bak-$TS.jar"
cp /tmp/nac-auth-new.jar app/nac-auth-1.0-SNAPSHOT.jar
md5sum app/nac-auth-1.0-SNAPSHOT.jar
bash nac.sh stop nac-auth || true
sleep 3
pkill -9 -f 'app/nac-auth-1.0-SNAPSHOT.jar' || true
sleep 1
bash nac.sh start nac-auth
sleep 9
ps -eo pid,lstart,cmd | grep 'app/nac-auth' | grep -v grep
ss -tulnp 2>/dev/null | grep -E ':1812' | head -2
tail -5 logs/nac-auth.out
